package com.kafein.todo.task.task.dto;

import com.kafein.todo.task.task.TaskStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Kanban sürükle-bırak: hedef kolon ve kolondaki hedef sıra (0 = en üst).
 * Kolon boyunu aşan sıra kolonun sonuna sınırlanır (istemcinin gördüğü board eski olabilir).
 */
public record MoveTaskRequest(
        @NotNull(message = "boş olamaz")
        TaskStatus status,
        @NotNull(message = "boş olamaz")
        @Min(value = 0, message = "0 veya daha büyük olmalı")
        Integer position) {
}
