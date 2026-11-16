package com.kafein.todo.task.task;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.access.ProjectAccess;
import com.kafein.todo.task.access.TaskAccessService;
import com.kafein.todo.task.task.dto.CreateTaskRequest;
import com.kafein.todo.task.task.dto.TaskResponse;
import com.kafein.todo.task.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Görev CRUD ve durum değiştirme.
 * <p>
 * Akış: yetki project-service'e Feign ile sorulur (transaction dışında: ağ çağrısı sırasında veritabanı
 * bağlantısı/kilit tutulmasın), sonra kısa bir transaction'da board kilidi alınır ({@link BoardLock}), görev
 * kilit altında yeniden okunur ve değiştirilir. {@code projectId} değişmediği için yetki sonucu geçerli kalır.
 * <p>
 * Pozisyon kuralı: kolon içinde 0'dan başlar, boşluksuz. Görev bir kolona girerken sona eklenir, çıkarken
 * arkasındakiler bir öne alınır.
 */
@Service
public class TaskService {

    private static final Sort LIST_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TaskRepository tasks;
    private final TaskAccessService access;
    private final BoardLock boardLock;
    private final TransactionTemplate tx;

    public TaskService(TaskRepository tasks, TaskAccessService access, BoardLock boardLock,
                       PlatformTransactionManager transactionManager) {
        this.tasks = tasks;
        this.access = access;
        this.boardLock = boardLock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public TaskResponse create(AuthenticatedUser user, CreateTaskRequest request) {
        long projectId = request.projectId();
        ProjectAccess projectAccess = access.requireWritable(user, projectId);
        access.requireAssignable(projectId, request.assigneeId());

        Task created = tx.execute(status -> {
            boardLock.lock(projectId);
            int position = tasks.findMaxPosition(projectId, TaskStatus.TODO) + 1;
            return tasks.saveAndFlush(new Task(projectId, request.title(), request.description(),
                    request.priorityOrDefault(), request.assigneeId(), user.id(), request.dueDate(), position));
        });
        return TaskResponse.from(created, projectAccess);
    }

    public TaskResponse get(AuthenticatedUser user, long taskId) {
        Task task = find(taskId);
        return TaskResponse.from(task, access.require(user, task.getProjectId()));
    }

    /** Projenin görevleri, en yeniler önce. */
    public PageResponse<TaskResponse> list(AuthenticatedUser user, long projectId, int page, int size) {
        ProjectAccess projectAccess = access.require(user, projectId);
        Page<Task> result = tasks.findByProjectId(projectId, PageRequest.of(page, size, LIST_SORT));
        return PageResponse.of(result.map(task -> TaskResponse.from(task, projectAccess)).getContent(),
                page, size, result.getTotalElements());
    }

    public TaskResponse update(AuthenticatedUser user, long taskId, UpdateTaskRequest request) {
        Task current = find(taskId);
        ProjectAccess projectAccess = access.requireWritable(user, current.getProjectId());
        // Atama değişmediyse yeniden sorulmaz: mevcut atamayı korumak her zaman serbest
        if (!Objects.equals(request.assigneeId(), current.getAssigneeId())) {
            access.requireAssignable(current.getProjectId(), request.assigneeId());
        }

        Task updated = tx.execute(status -> {
            boardLock.lock(current.getProjectId());
            Task task = find(taskId);
            task.update(request.title(), request.description(), request.priority(), request.assigneeId(),
                    request.dueDate());
            return tasks.saveAndFlush(task);
        });
        return TaskResponse.from(updated, projectAccess);
    }

    /** Görevi hedef kolonun sonuna taşır; aynı kolonsa değişiklik yapmaz. */
    public TaskResponse changeStatus(AuthenticatedUser user, long taskId, TaskStatus target) {
        Task current = find(taskId);
        ProjectAccess projectAccess = access.requireWritable(user, current.getProjectId());

        Task changed = tx.execute(status -> {
            long projectId = current.getProjectId();
            boardLock.lock(projectId);
            Task task = find(taskId);
            if (task.getStatus() == target) {
                return task;
            }
            TaskStatus fromStatus = task.getStatus();
            int fromPosition = task.getPosition();
            task.placeAt(target, tasks.findMaxPosition(projectId, target) + 1);
            tasks.saveAndFlush(task);
            tasks.closeGap(projectId, fromStatus, fromPosition);
            return task;
        });
        return TaskResponse.from(changed, projectAccess);
    }

    /** Reporter / OWNER / ADMIN. Kolondaki boşluk kapatılır. */
    public void delete(AuthenticatedUser user, long taskId) {
        Task current = find(taskId);
        ProjectAccess projectAccess = access.requireWritable(user, current.getProjectId());
        if (!projectAccess.canDelete(current)) {
            throw ApiException.forbidden("TASK_DELETE_FORBIDDEN",
                    "Görevi yalnızca oluşturan kişi, proje sahibi veya yönetici silebilir");
        }

        tx.executeWithoutResult(status -> {
            long projectId = current.getProjectId();
            boardLock.lock(projectId);
            // Bu arada başkası sildiyse sonuç aynı: idempotent
            tasks.findById(taskId).ifPresent(task -> {
                tasks.delete(task);
                tasks.flush();
                tasks.closeGap(projectId, task.getStatus(), task.getPosition());
            });
        });
    }

    private Task find(long taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Görev bulunamadı"));
    }
}
