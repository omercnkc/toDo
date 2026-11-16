package com.kafein.todo.task.task;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.task.dto.ChangeStatusRequest;
import com.kafein.todo.task.task.dto.CreateTaskRequest;
import com.kafein.todo.task.task.dto.TaskResponse;
import com.kafein.todo.task.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Görev uçları (docs/06 "task-service"). Yetki: proje üyesi veya ADMIN; silme reporter / OWNER / ADMIN. */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Yeni görev {@code TODO} kolonunun sonuna eklenir; oluşturan kişi reporter olur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(user, request);
    }

    /** Projenin görevleri, en yeniler önce. Arama/filtre/sıralama Faz 5'te. */
    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam long projectId,
                                           @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 veya daha büyük olmalı") int page,
                                           @RequestParam(defaultValue = "20")
                                           @Min(value = 1, message = "en az 1 olmalı")
                                           @Max(value = 100, message = "en fazla 100 olabilir") int size) {
        return taskService.list(user, projectId, page, size);
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        taskService.delete(user, id);
    }
}
