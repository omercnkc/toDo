package com.kafein.todo.task.task.dto;

import com.kafein.todo.task.access.ProjectAccess;
import com.kafein.todo.task.task.Task;
import com.kafein.todo.task.task.TaskPriority;
import com.kafein.todo.task.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * docs/06 "Görev yanıtı". Atanan kişinin adı dönmez (karar: frontend proje üye listesinden eşler).
 *
 * @param canDelete sil düğmesi gösterilsin mi (reporter / OWNER / ADMIN). Arayüz kolaylığı içindir;
 *                  silmede yetki sunucuda yeniden kontrol edilir.
 */
public record TaskResponse(Long id, Long projectId, String title, String description, TaskStatus status,
                           TaskPriority priority, int position, Long assigneeId, Long reporterId, LocalDate dueDate,
                           boolean canDelete, Instant createdAt, Instant updatedAt) {

    public static TaskResponse from(Task task, ProjectAccess access) {
        return new TaskResponse(task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getPosition(), task.getAssigneeId(), task.getReporterId(),
                task.getDueDate(), access.canDelete(task), task.getCreatedAt(), task.getUpdatedAt());
    }
}
