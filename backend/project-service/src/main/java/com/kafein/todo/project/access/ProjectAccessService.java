package com.kafein.todo.project.access;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import com.kafein.todo.project.project.Project;
import com.kafein.todo.project.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proje bazlı yetki (docs/07): üye görür, OWNER/ADMIN yönetir. Rol tabanlı {@code @PreAuthorize}
 * burada yetmez çünkü yetki projeye göre değişir; bu yüzden servis katmanında kontrol edilir.
 * <p>
 * Olmayan proje → 404; var olan ama erişimi olmayan proje → 403 (docs/10 bitiş kriteri: "yetkisiz erişim 403").
 */
@Service
public class ProjectAccessService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectAccessService(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    /** Projeyi görebilir mi (üye veya ADMIN)? */
    @Transactional(readOnly = true)
    public ProjectAccess requireMember(long projectId, AuthenticatedUser user) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Proje bulunamadı"));
        MemberRole myRole = members.findByProjectIdAndUserId(projectId, user.id())
                .map(ProjectMember::getRole)
                .orElse(null);
        if (myRole == null && !user.isAdmin()) {
            throw ApiException.forbidden("PROJECT_ACCESS_DENIED", "Bu projeye erişim yetkiniz yok");
        }
        return new ProjectAccess(project, myRole, user.isAdmin());
    }

    /** Projeyi yönetebilir mi (OWNER veya ADMIN)? */
    @Transactional(readOnly = true)
    public ProjectAccess requireManager(long projectId, AuthenticatedUser user) {
        ProjectAccess access = requireMember(projectId, user);
        if (!access.canManage()) {
            throw ApiException.forbidden("PROJECT_MANAGE_DENIED",
                    "Bu işlem için proje sahibi veya yönetici olmalısınız");
        }
        return access;
    }
}
