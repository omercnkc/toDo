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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Görevler", description = "Görev CRUD, Kanban panosu, filtreli liste ve dashboard sayıları. Yetki her istekte project-service'e sorulur.")
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
    @Operation(summary = "Görev oluştur",
            description = "Proje üyesi / ADMIN. Durum TODO, kolonun sonuna eklenir; `priority` boşsa MEDIUM; oluşturan kişi reporter olur. `assigneeId` proje üyesi olmalı.")
    @ApiResponse(responseCode = "201", description = "Görev oluşturuldu")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR / ASSIGNEE_NOT_MEMBER (`fieldErrors[assigneeId]`)")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki projede değişiklik yapılamaz")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
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
    @Operation(summary = "Görev listesi (filtre, sıralama, sayfalama)",
            description = "`projectId` verilmezse üye olunan tüm projeler (arşivdekiler dahil). Filtreler VE ile, çok değerli filtrenin değerleri VEYA ile bağlanır: `status`, `priority` (virgülle ya da tekrarla), `assigneeId` (id ya da `none`), `q` (başlık veya açıklama, Türkçe kurallarla harf/aksan duyarsız, en fazla 200), `dueFrom`/`dueTo` (YYYY-AA-GG, dahil), `overdue=true`. `sort=alan[,asc|desc]` tekrarlanabilir: dueDate, priority, createdAt, title, status (varsayılan createdAt,desc). Hatalı parametreler tek yanıtta: 400 + her parametre için `fieldErrors`.")
    @ApiResponse(responseCode = "200", description = "Sayfalı liste")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(required = false) Long projectId,
                                           @ParameterObject @ModelAttribute TaskListParams params,
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
    @Operation(summary = "Dashboard sayıları",
            description = "Görev değil yalnızca sayılar. `scope=mine` (varsayılan) üye olunan projeler; `scope=all` tüm sistem, yalnızca ADMIN. \"Gecikmiş\" = bitiş tarihi bugünden önce ve DONE değil.")
    @ApiResponse(responseCode = "200", description = "Proje başına sayılar + bana atananlar")
    @ApiResponse(responseCode = "403", description = "STATS_SCOPE_FORBIDDEN — `scope=all` yalnızca ADMIN")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @GetMapping("/stats")
    public TaskStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) String scope) {
        return statsService.stats(user, scope);
    }

    /** Kanban: {@code {"TODO":[..],"IN_PROGRESS":[..],"DONE":[..]}}, her kolon pozisyona göre sıralı. */
    @Operation(summary = "Kanban panosu",
            description = "`{\"TODO\": [..], \"IN_PROGRESS\": [..], \"DONE\": [..]}`; üç anahtar her zaman var, her kolon `position`'a göre sıralı. Arşivdeki projede de okunur.")
    @ApiResponse(responseCode = "200", description = "Kolonlar")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @GetMapping("/board")
    public Map<TaskStatus, List<TaskResponse>> board(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam long projectId) {
        return taskService.board(user, projectId);
    }

    @Operation(summary = "Görev ayrıntısı")
    @ApiResponse(responseCode = "200", description = "Görev")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return taskService.get(user, id);
    }

    @Operation(summary = "Görevi düzenle",
            description = "Tüm düzenlenebilir alanlar değişir (gönderilmeyen `assigneeId`/`dueDate` boşalır); durum ve sıra değişmez.")
    @ApiResponse(responseCode = "200", description = "Güncel görev")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR / ASSIGNEE_NOT_MEMBER (`fieldErrors[assigneeId]`)")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki projede değişiklik yapılamaz")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(user, id, request);
    }

    /** Görevi hedef kolonun sonuna taşır. */
    @Operation(summary = "Durum değiştir",
            description = "Görev hedef kolonun sonuna geçer, eski kolondaki boşluk kapanır; aynı durumsa değişiklik yok.")
    @ApiResponse(responseCode = "200", description = "Güncel görev")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki projede değişiklik yapılamaz")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return taskService.changeStatus(user, id, request.status());
    }

    /** Kanban sürükle-bırak: hedef kolon + kolondaki sıra; diğer görevler kaydırılır. */
    @Operation(summary = "Kanban'da taşı (sürükle-bırak)",
            description = "`{status, position}`; `position` ≥ 0, kolon boyunu aşarsa kolonun sonuna sınırlanır. Aradaki görevler kaydırılır.")
    @ApiResponse(responseCode = "200", description = "Taşınan görev")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki projede değişiklik yapılamaz")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @PatchMapping("/{id}/move")
    public TaskResponse move(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                             @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move(user, id, request.status(), request.position());
    }

    @Operation(summary = "Görevi sil",
            description = "Görevi oluşturan (reporter), projenin OWNER'ı ya da ADMIN.")
    @ApiResponse(responseCode = "204", description = "Silindi")
    @ApiResponse(responseCode = "403", description = "TASK_DELETE_FORBIDDEN / PROJECT_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "TASK_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki projede değişiklik yapılamaz")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE — project-service'e ulaşılamadı")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        taskService.delete(user, id);
    }
}
