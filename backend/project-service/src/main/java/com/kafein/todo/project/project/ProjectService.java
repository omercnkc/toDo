package com.kafein.todo.project.project;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.persistence.LikePatterns;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.access.ProjectAccess;
import com.kafein.todo.project.access.ProjectAccessService;
import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import com.kafein.todo.project.project.dto.CreateProjectRequest;
import com.kafein.todo.project.project.dto.ProjectResponse;
import com.kafein.todo.project.project.dto.UpdateProjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    /** Son güncellenen önce; eşitlikte yeni oluşturulan önce (sayfalar arası kararlı sıra). */
    private static final Sort LIST_SORT = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccessService access;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccessService access) {
        this.projects = projects;
        this.members = members;
        this.access = access;
    }

    /** Proje ve oluşturanın OWNER üyeliği tek transaction'da. */
    @Transactional
    public ProjectResponse create(AuthenticatedUser user, CreateProjectRequest request) {
        Project project = projects.save(new Project(request.name(), request.description(), user.id()));
        members.save(ProjectMember.owner(project, user.id()));
        projects.flush(); // created_at/updated_at yanıtta dolu olsun
        log.info("Kullanıcı {} proje {} oluşturdu", user.id(), project.getId());
        return ProjectResponse.from(project, MemberRole.OWNER, user.isAdmin(), 1);
    }

    /** USER: üyesi olduğu projeler. ADMIN: tüm projeler. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(AuthenticatedUser user, String q, ProjectStatus status, int page, int size) {
        Long memberFilter = user.isAdmin() ? null : user.id();
        Page<Project> result = projects.search(memberFilter, LikePatterns.contains(q), status,
                PageRequest.of(page, size, LIST_SORT));

        List<Long> ids = result.getContent().stream().map(Project::getId).toList();
        Map<Long, Long> memberCounts = ids.isEmpty() ? Map.of() : members.countByProjectIds(ids).stream()
                .collect(Collectors.toMap(ProjectMemberRepository.MemberCount::getProjectId,
                        ProjectMemberRepository.MemberCount::getMemberCount));
        Map<Long, MemberRole> myRoles = ids.isEmpty() ? Map.of() : members.findByUserIdAndProjectIdIn(user.id(), ids)
                .stream()
                .collect(Collectors.toMap(m -> m.getProject().getId(), ProjectMember::getRole));

        List<ProjectResponse> content = result.getContent().stream()
                .map(p -> ProjectResponse.from(p, myRoles.get(p.getId()), user.isAdmin(),
                        memberCounts.getOrDefault(p.getId(), 0L)))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(AuthenticatedUser user, long projectId) {
        return toResponse(access.requireMember(projectId, user));
    }

    /**
     * Ad/açıklama ve isteğe bağlı durum. Arşivlenmiş proje yalnızca aynı istekte {@code status=ACTIVE}
     * ile geri alınırken düzenlenebilir.
     */
    @Transactional
    public ProjectResponse update(AuthenticatedUser user, long projectId, UpdateProjectRequest request) {
        ProjectAccess projectAccess = access.requireManager(projectId, user);
        Project project = projectAccess.project();
        ProjectStatus newStatus = request.status() != null ? request.status() : project.getStatus();
        if (project.isArchived() && newStatus == ProjectStatus.ARCHIVED) {
            throw ApiException.conflict("PROJECT_ARCHIVED",
                    "Arşivlenmiş proje düzenlenemez; önce arşivden çıkarın");
        }
        project.update(request.name(), request.description());
        project.changeStatus(newStatus);
        projects.flush(); // updated_at yanıtta güncel olsun
        return toResponse(projectAccess);
    }

    /**
     * Arşivleme (yumuşak silme). İdempotent: zaten arşivdeyse de 204.
     * Kalıcı silme (görevlerin outbox ile temizlenmesi) Faz 4'te.
     */
    @Transactional
    public void archive(AuthenticatedUser user, long projectId, boolean permanent) {
        if (permanent) {
            throw ApiException.badRequest("PERMANENT_DELETE_NOT_AVAILABLE", "Kalıcı silme henüz desteklenmiyor");
        }
        Project project = access.requireManager(projectId, user).project();
        if (!project.isArchived()) {
            project.changeStatus(ProjectStatus.ARCHIVED);
            log.info("Kullanıcı {} proje {}'i arşivledi", user.id(), projectId);
        }
    }

    private ProjectResponse toResponse(ProjectAccess projectAccess) {
        Project project = projectAccess.project();
        return ProjectResponse.from(project, projectAccess.myRole(), projectAccess.admin(),
                members.countByProjectId(project.getId()));
    }
}
