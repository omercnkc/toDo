package com.kafein.todo.task.client;

/** project-service {@code ProjectResponse}'unun bu servisin ihtiyaç duyduğu kısmı (diğer alanlar yok sayılır). */
public record ProjectSummary(Long id, String name, ProjectStatus status) {

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }
}
