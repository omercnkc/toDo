package com.kafein.todo.project.member;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.access.ProjectAccess;
import com.kafein.todo.project.access.ProjectAccessService;
import com.kafein.todo.project.client.UserDirectory;
import com.kafein.todo.project.client.UserSummary;
import com.kafein.todo.project.member.dto.MemberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final ProjectMemberRepository members;
    private final ProjectAccessService access;
    private final UserDirectory users;

    public MemberService(ProjectMemberRepository members, ProjectAccessService access, UserDirectory users) {
        this.members = members;
        this.access = access;
        this.users = users;
    }

    /**
     * Önce OWNER, sonra katılma sırası. Ad/e-posta tek toplu çağrıyla auth-service'ten.
     * auth-service'e ulaşılamazsa liste adsız döner (üyelik bilgisi project-service'in kendi verisi;
     * bir servisin kesintisi tüm ekranı düşürmesin).
     * <p>
     * Transaction yok: uzak çağrı sürerken veritabanı bağlantısı tutulmasın.
     */
    public List<MemberResponse> list(AuthenticatedUser user, long projectId) {
        access.requireMember(projectId, user);
        List<ProjectMember> projectMembers = members.findByProjectIdOrderByRoleDescJoinedAtAsc(projectId);
        Map<Long, UserSummary> summaries =
                users.lookupBestEffort(projectMembers.stream().map(ProjectMember::getUserId).toList());
        return projectMembers.stream()
                .map(m -> MemberResponse.from(m, summaries.get(m.getUserId())))
                .toList();
    }

    /**
     * OWNER/ADMIN üye çıkarır. OWNER çıkarılamaz (409); arşivlenmiş projede üyelik değişmez (409).
     * Faz 4: çıkarılan kişiye atanmış görevlerin {@code assignee_id}'si task-service'te boşaltılacak.
     */
    @Transactional
    public void remove(AuthenticatedUser user, long projectId, long memberUserId) {
        ProjectAccess projectAccess = access.requireManager(projectId, user);
        if (projectAccess.project().isArchived()) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "Arşivlenmiş projede üyelik değiştirilemez");
        }
        ProjectMember member = members.findByProjectIdAndUserId(projectId, memberUserId)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Bu kullanıcı projenin üyesi değil"));
        if (member.isOwner()) {
            throw ApiException.conflict("OWNER_CANNOT_BE_REMOVED", "Proje sahibi projeden çıkarılamaz");
        }
        members.delete(member);
        log.info("Kullanıcı {} proje {}'den üye {}'i çıkardı", user.id(), projectId, memberUserId);
    }
}
