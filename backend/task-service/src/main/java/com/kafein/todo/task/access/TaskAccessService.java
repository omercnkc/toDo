package com.kafein.todo.task.access;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.client.ProjectDirectory;
import com.kafein.todo.task.client.ProjectMembership;
import com.kafein.todo.task.client.ProjectSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Proje bazlı yetki: her görev işleminde project-service'e sorulur (docs/04 "Her yazma işleminde üyelik doğrulaması").
 * <ul>
 *   <li>Üye → erişir.</li>
 *   <li>Üye olmayan ADMIN → proje varsa erişir, yoksa 404 {@code PROJECT_NOT_FOUND}.</li>
 *   <li>Diğerleri → 403 {@code PROJECT_ACCESS_DENIED}. Proje var mı yok mu ayrımı yapılmaz: project-service
 *       üye olmayana bunu söylemediği için (404 tek yanıt) task-service de söylemez.</li>
 * </ul>
 * Feign çağrısı yaptığı için transaction dışında çağrılır.
 */
@Service
public class TaskAccessService {

    private final ProjectDirectory projects;

    public TaskAccessService(ProjectDirectory projects) {
        this.projects = projects;
    }

    /** Okuma yetkisi. */
    public ProjectAccess require(AuthenticatedUser user, long projectId) {
        Optional<ProjectMembership> membership = projects.membership(projectId, user.id());
        if (membership.isPresent()) {
            ProjectMembership m = membership.get();
            return new ProjectAccess(projectId, user.id(), m.role(), user.isAdmin(), m.isProjectArchived());
        }
        if (user.isAdmin()) {
            ProjectSummary project = projects.project(projectId)
                    .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Proje bulunamadı"));
            return new ProjectAccess(projectId, user.id(), null, true, project.isArchived());
        }
        throw ApiException.forbidden("PROJECT_ACCESS_DENIED", "Bu projeye erişim yetkiniz yok");
    }

    /**
     * "Benim projelerim" kapsamı: kullanıcının üye olduğu her proje için okuma yetkisi (proje id'sine göre).
     * ADMIN için de yalnızca üyesi olduğu projeler: sistemdeki tüm görevleri tek listede açmak bu ucun işi değil
     * (üye olmayan ADMIN tek tek {@code projectId} ile erişir).
     */
    public Map<Long, ProjectAccess> requireMemberships(AuthenticatedUser user) {
        Map<Long, ProjectAccess> result = new LinkedHashMap<>();
        for (ProjectMembership m : projects.memberships(user.id())) {
            result.put(m.projectId(),
                    new ProjectAccess(m.projectId(), user.id(), m.role(), user.isAdmin(), m.isProjectArchived()));
        }
        return result;
    }

    /** Yazma yetkisi: okuma + proje arşivde değil. */
    public ProjectAccess requireWritable(AuthenticatedUser user, long projectId) {
        ProjectAccess access = require(user, projectId);
        if (access.archived()) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "Arşivlenmiş projenin görevleri değiştirilemez");
        }
        return access;
    }

    /** Atanacak kişi projenin üyesi olmalı ({@code null} = atanmamış, her zaman geçerli). */
    public void requireAssignable(long projectId, Long assigneeId) {
        if (assigneeId != null && projects.membership(projectId, assigneeId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_MEMBER",
                    "Görev yalnızca proje üyelerine atanabilir")
                    .withField("assigneeId", "proje üyesi değil");
        }
    }
}
