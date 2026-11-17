package com.kafein.todo.dashboard.client;

/**
 * project-service {@code MembershipResponse} karşılığı. Rol ve durum metin olarak tutulur: dashboard bunlarla
 * yalnızca gösterim/ayırım yapar, project-service yeni bir değer eklerse çözümleme bozulmasın.
 *
 * @param role          {@code OWNER} / {@code MEMBER}
 * @param projectStatus {@code ACTIVE} / {@code ARCHIVED}
 */
public record ProjectMembership(Long projectId, String projectName, Long userId, String role, String projectStatus) {

    public boolean isArchived() {
        return "ARCHIVED".equals(projectStatus);
    }
}
