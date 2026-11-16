package com.kafein.todo.task.client;

/** project-service {@code MembershipResponse} karşılığı. */
public record ProjectMembership(Long projectId, Long userId, ProjectRole role, ProjectStatus projectStatus) {

    public boolean isOwner() {
        return role == ProjectRole.OWNER;
    }

    public boolean isProjectArchived() {
        return projectStatus == ProjectStatus.ARCHIVED;
    }
}
