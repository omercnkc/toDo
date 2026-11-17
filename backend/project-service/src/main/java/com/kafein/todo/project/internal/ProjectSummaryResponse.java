package com.kafein.todo.project.internal;

import com.kafein.todo.project.project.Project;
import com.kafein.todo.project.project.ProjectStatus;

/** Dahili proje özeti (dashboard-service, ADMIN "tüm sistem" kapsamı). */
public record ProjectSummaryResponse(Long id, String name, ProjectStatus status) {

    public static ProjectSummaryResponse from(Project project) {
        return new ProjectSummaryResponse(project.getId(), project.getName(), project.getStatus());
    }
}
