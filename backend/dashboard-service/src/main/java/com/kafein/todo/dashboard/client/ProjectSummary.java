package com.kafein.todo.dashboard.client;

/** project-service {@code ProjectSummaryResponse} karşılığı. @param status {@code ACTIVE} / {@code ARCHIVED} */
public record ProjectSummary(Long id, String name, String status) {
}
