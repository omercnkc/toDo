package com.kafein.todo.project.project;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.project.dto.CreateProjectRequest;
import com.kafein.todo.project.project.dto.ProjectResponse;
import com.kafein.todo.project.project.dto.UpdateProjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway'den {@code /api/projects/**} → burada {@code /projects/**} (docs/06). Hepsi giriş gerektirir;
 * proje bazlı yetki (üye / OWNER / ADMIN) {@code ProjectAccessService}'te.
 */
@Tag(name = "Projeler", description = "Proje oluşturma, listeleme, düzenleme, arşivleme ve kalıcı silme.")
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** Oluşturan kişi OWNER olur. */
    @Operation(summary = "Proje oluştur",
            description = "Oluşturan kişi OWNER olur. Ad 1–150, açıklama en fazla 5000 karakter.")
    @ApiResponse(responseCode = "201", description = "Proje oluşturuldu")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(user, request);
    }

    /** USER: üyesi olduğu projeler; ADMIN: tümü. {@code q} proje adında arar; {@code status} boşsa hepsi. */
    @Operation(summary = "Projelerim",
            description = "USER: üyesi olduğu projeler; ADMIN: tümü. `q` proje adında \"içerir\" araması (`%` `_` joker sayılmaz); `status` ACTIVE / ARCHIVED, boşsa hepsi. Son güncellenen önce; `size` 1–100.")
    @ApiResponse(responseCode = "200", description = "Sayfalı liste")
    @GetMapping
    public PageResponse<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) ProjectStatus status,
                                              @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 veya daha büyük olmalı") int page,
                                              @RequestParam(defaultValue = "20")
                                              @Min(value = 1, message = "en az 1 olmalı")
                                              @Max(value = 100, message = "en fazla 100 olabilir") int size) {
        return projectService.list(user, q, status, page, size);
    }

    @Operation(summary = "Proje ayrıntısı",
            description = "Üye veya ADMIN.")
    @ApiResponse(responseCode = "200", description = "Proje")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return projectService.get(user, id);
    }

    /** OWNER/ADMIN. Arşivden geri almak için {@code "status": "ACTIVE"}. */
    @Operation(summary = "Projeyi düzenle",
            description = "OWNER / ADMIN. Tam güncelleme: gönderilmeyen `description` silinir, `status` boşsa değişmez. Arşivdeki proje yalnızca aynı istekte `\"status\": \"ACTIVE\"` ile geri alınırken düzenlenebilir.")
    @ApiResponse(responseCode = "200", description = "Güncel proje")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "PROJECT_ARCHIVED — arşivdeki proje düzenlenemez")
    @PutMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(user, id, request);
    }

    /** OWNER/ADMIN: arşivler (204). {@code ?permanent=true}: kalıcı siler (204), görevler outbox ile asenkron temizlenir. */
    @Operation(summary = "Projeyi arşivle / kalıcı sil",
            description = "OWNER / ADMIN. Varsayılan arşivler (zaten arşivdeyse de 204). `permanent=true`: proje, üyeler ve davetler hemen, görevler outbox ile asenkron silinir; geri alınamaz.")
    @ApiResponse(responseCode = "204", description = "Arşivlendi / silindi")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                        @RequestParam(defaultValue = "false") boolean permanent) {
        projectService.archive(user, id, permanent);
    }
}
