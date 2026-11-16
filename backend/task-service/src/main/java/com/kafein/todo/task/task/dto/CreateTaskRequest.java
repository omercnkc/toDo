package com.kafein.todo.task.task.dto;

import com.kafein.todo.task.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yeni görev her zaman {@code TODO} kolonunun sonuna eklenir.
 *
 * @param priority   boşsa {@code MEDIUM}
 * @param assigneeId boşsa atanmamış; doluysa proje üyesi olmalı
 */
public record CreateTaskRequest(
        @NotNull(message = "boş olamaz")
        Long projectId,
        @NotBlank(message = "boş olamaz")
        @Size(max = TaskLimits.TITLE_MAX, message = "en fazla " + TaskLimits.TITLE_MAX + " karakter olabilir")
        String title,
        @Size(max = TaskLimits.DESCRIPTION_MAX, message = "en fazla " + TaskLimits.DESCRIPTION_MAX + " karakter olabilir")
        String description,
        TaskPriority priority,
        Long assigneeId,
        LocalDate dueDate) {

    public TaskPriority priorityOrDefault() {
        return priority == null ? TaskPriority.MEDIUM : priority;
    }
}
