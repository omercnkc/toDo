package com.kafein.todo.task.client;

/** project-service {@code ProjectStatus} karşılığı (JSON'da adıyla gelir). */
public enum ProjectStatus {
    ACTIVE,
    /** Görevler korunur ama değiştirilemez (görev yazma işlemleri 409). */
    ARCHIVED
}
