package com.kafein.todo.task.task;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.stats.TaskStatsResponse;
import com.kafein.todo.task.stats.TaskStatsService;
import com.kafein.todo.task.task.dto.ChangeStatusRequest;
import com.kafein.todo.task.task.dto.CreateTaskRequest;
import com.kafein.todo.task.task.dto.MoveTaskRequest;
import com.kafein.todo.task.task.dto.TaskResponse;
import com.kafein.todo.task.task.dto.UpdateTaskRequest;
import com.kafein.todo.task.task.query.TaskFilter;
import com.kafein.todo.task.task.query.TaskListParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Görev uçları (docs/06 "task-service"). Yetki: proje üyesi veya ADMIN; silme reporter / OWNER / ADMIN. */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskStatsService statsService;

    public TaskController(TaskService taskService, TaskStatsService statsService) {
        this.taskService = taskService;
        this.statsService = statsService;
    }

    /** Yeni görev {@code TODO} kolonunun sonuna eklenir; oluşturan kişi reporter olur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(user, request);
    }

    /**
     * Görev listesi: filtreler, sıralama ve sayfalama (docs/06 "Liste filtreleri"). Varsayılan sıra en
     * yeniler önce. Filtre parametreleri {@link TaskListParams}'ta; hatalıysa 400 + {@code fieldErrors}.
     * {@code projectId} yoksa kullanıcının üye olduğu tüm projelerin görevleri.
     */
    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(required = false) Long projectId,
                                           @ModelAttribute TaskListParams params,
                                           @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 veya daha büyük olmalı") int page,
                                           @RequestParam(defaultValue = "20")
                                           @Min(value = 1, message = "en az 1 olmalı")
                                           @Max(value = 100, message = "en fazla 100 olabilir") int size) {
        return taskService.list(user, projectId, TaskFilter.parse(params), page, size);
    }

    /**
     * Dashboard sayıları (proje başına durum/gecikmiş, bana atanan açık görevler). {@code scope=mine} (varsayılan)
     * üye olunan projeler, {@code scope=all} tüm sistem (yalnızca ADMIN).
     */
    @GetMapping("/stats")
    public TaskStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) String scope) {
        return statsService.stats(user, scope);
    }

    /** Kanban: {@code {"TODO":[..],"IN_PROGRESS":[..],"DONE":[..]}}, her kolon pozisyona göre sıralı. */
    @GetMapping("/board")
    public Map<TaskStatus, List<TaskResponse>> board(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam long projectId) {
        return taskService.board(user, projectId);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return taskService.get(user, id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(user, id, request);
    }

    /** Görevi hedef kolonun sonuna taşır. */
    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return taskService.changeStatus(user, id, request.status());
    }

    /** Kanban sürükle-bırak: hedef kolon + kolondaki sıra; diğer görevler kaydırılır. */
    @PatchMapping("/{id}/move")
    public TaskResponse move(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                             @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move(user, id, request.status(), request.position());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        taskService.delete(user, id);
    }
}
