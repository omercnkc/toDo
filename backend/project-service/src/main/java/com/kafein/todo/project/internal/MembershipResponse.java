package com.kafein.todo.project.internal;

import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.project.ProjectStatus;

/** Dahili üyelik yanıtı (task-service). */
public record MembershipResponse(Long projectId, Long userId, MemberRole role, ProjectStatus projectStatus) {

    public static MembershipResponse from(ProjectMember member) {
        return new MembershipResponse(member.getProject().getId(), member.getUserId(), member.getRole(),
                member.getProject().getStatus());
    }
}
