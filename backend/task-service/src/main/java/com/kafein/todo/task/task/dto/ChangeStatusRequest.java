package com.kafein.todo.task.task.dto;

import com.kafein.todo.task.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

/** Görev hedef kolonun sonuna geçer (sıra seçmek için {@code /move}). */
public record ChangeStatusRequest(@NotNull(message = "boş olamaz") TaskStatus status) {
}
