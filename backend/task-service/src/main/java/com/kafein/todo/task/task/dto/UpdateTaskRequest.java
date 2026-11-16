package com.kafein.todo.task.task.dto;

import com.kafein.todo.task.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Görevin düzenlenebilir alanlarının tamamı (PUT: gönderilmeyen {@code assigneeId}/{@code dueDate} boşaltılır).
 * Durum ve sıra burada değişmez: {@code PATCH /tasks/{id}/status} ve {@code /move}.
 */
public record UpdateTaskRequest(
        @NotBlank(message = "boş olamaz")
        @Size(max = TaskLimits.TITLE_MAX, message = "en fazla " + TaskLimits.TITLE_MAX + " karakter olabilir")
        String title,
        @Size(max = TaskLimits.DESCRIPTION_MAX, message = "en fazla " + TaskLimits.DESCRIPTION_MAX + " karakter olabilir")
        String description,
        @NotNull(message = "boş olamaz")
        TaskPriority priority,
        Long assigneeId,
        LocalDate dueDate) {
}
