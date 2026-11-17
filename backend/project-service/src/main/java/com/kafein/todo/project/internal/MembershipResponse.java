package com.kafein.todo.project.internal;

import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.project.ProjectStatus;

/**
 * Dahili üyelik yanıtı (task-service, dashboard-service).
 *
 * @param projectName dashboard proje listesini ayrıca çekmesin diye (6-A); task-service kullanmaz
 */
public record MembershipResponse(Long projectId, String projectName, Long userId, MemberRole role,
                                 ProjectStatus projectStatus) {

    public static MembershipResponse from(ProjectMember member) {
        return new MembershipResponse(member.getProject().getId(), member.getProject().getName(), member.getUserId(),
                member.getRole(), member.getProject().getStatus());
    }
}
