package com.kafein.todo.task.task;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.access.ProjectAccess;
import com.kafein.todo.task.access.TaskAccessService;
import com.kafein.todo.task.config.TaskProperties;
import com.kafein.todo.task.task.dto.CreateTaskRequest;
import com.kafein.todo.task.task.dto.TaskResponse;
import com.kafein.todo.task.task.dto.UpdateTaskRequest;
import com.kafein.todo.task.task.query.TaskFilter;
import com.kafein.todo.task.task.query.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    private final TaskRepository tasks;
    private final TaskAccessService access;
    private final BoardLock boardLock;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public TaskService(TaskRepository tasks, TaskAccessService access, BoardLock boardLock,
                       PlatformTransactionManager transactionManager, Clock clock, TaskProperties properties) {
        this.tasks = tasks;
        this.access = access;
        this.boardLock = boardLock;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.zone = properties.timeZone();
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

    /**
     * Görev listesi; filtre ve sıralama {@link TaskSpecifications}'ta. Yetki önce kontrol edilir: erişimi
     * olmayan, filtre denemeleriyle görev olup olmadığını öğrenemesin.
     *
     * @param projectId tek proje; {@code null} ise kullanıcının üye olduğu tüm projeler ("benim projelerim")
     */
    public PageResponse<TaskResponse> list(AuthenticatedUser user, Long projectId, TaskFilter filter, int page,
                                           int size) {
        Map<Long, ProjectAccess> scope = projectId != null
                ? Map.of(projectId, access.require(user, projectId))
                : access.requireMemberships(user);
        if (scope.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Page<Task> result = tasks.findAll(TaskSpecifications.matching(scope.keySet(), filter, today),
                PageRequest.of(page, size));
        return PageResponse.of(
                result.map(task -> TaskResponse.from(task, scope.get(task.getProjectId()))).getContent(),
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

    /** Board: üç kolon da (boşsa boş liste), her biri pozisyona göre sıralı. */
    public Map<TaskStatus, List<TaskResponse>> board(AuthenticatedUser user, long projectId) {
        ProjectAccess projectAccess = access.require(user, projectId);
        Map<TaskStatus, List<TaskResponse>> columns = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            columns.put(status, new ArrayList<>());
        }
        for (Task task : tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId)) {
            columns.get(task.getStatus()).add(TaskResponse.from(task, projectAccess));
        }
        return columns;
    }

    /** Görevi hedef kolonun sonuna taşır; aynı kolonsa değişiklik yapmaz. */
    public TaskResponse changeStatus(AuthenticatedUser user, long taskId, TaskStatus target) {
        return moveInternal(user, taskId, target, null);
    }

    /**
     * Kanban sürükle-bırak: görevi {@code target} kolonunun {@code position} sırasına koyar. Sıra kolon boyunu
     * aşarsa kolonun sonuna sınırlanır. Kolon içi ve kolonlar arası taşımada diğer görevler kaydırılır,
     * pozisyonlar boşluksuz kalır.
     */
    public TaskResponse move(AuthenticatedUser user, long taskId, TaskStatus target, int position) {
        return moveInternal(user, taskId, target, position);
    }

    /** {@code requestedPosition == null}: hedef kolonun sonu, görev zaten o kolondaysa yerinde kalır. */
    private TaskResponse moveInternal(AuthenticatedUser user, long taskId, TaskStatus target,
                                      Integer requestedPosition) {
        Task current = find(taskId);
        ProjectAccess projectAccess = access.requireWritable(user, current.getProjectId());

        Task moved = tx.execute(status -> {
            long projectId = current.getProjectId();
            boardLock.lock(projectId);
            Task task = find(taskId);
            TaskStatus fromStatus = task.getStatus();
            int from = task.getPosition();
            boolean sameColumn = fromStatus == target;
            if (sameColumn && requestedPosition == null) {
                return task;
            }

            // Hedef kolonun boyu (görev zaten oradaysa kendisi hariç) = verilebilecek en büyük sıra
            int targetSize = tasks.findMaxPosition(projectId, target) + (sameColumn ? 0 : 1);
            int to = requestedPosition == null ? targetSize : Math.min(requestedPosition, targetSize);
            if (sameColumn && to == from) {
                return task;
            }

            // Toplu güncellemeler persistence context'i temizler; görev ayrıca kaydedilir (merge)
            if (sameColumn) {
                if (to < from) {
                    tasks.shift(projectId, target, to, from - 1, +1);   // aradakiler aşağı
                } else {
                    tasks.shift(projectId, target, from + 1, to, -1);   // aradakiler yukarı
                }
            } else {
                tasks.closeGap(projectId, fromStatus, from);
                tasks.shift(projectId, target, to, Integer.MAX_VALUE, +1); // hedefte yer aç
            }
            task.placeAt(target, to);
            return tasks.saveAndFlush(task);
        });
        return TaskResponse.from(moved, projectAccess);
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
