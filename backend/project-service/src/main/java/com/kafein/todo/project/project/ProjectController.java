package com.kafein.todo.project.project;

import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.project.dto.CreateProjectRequest;
import com.kafein.todo.project.project.dto.ProjectResponse;
import com.kafein.todo.project.project.dto.UpdateProjectRequest;
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
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** Oluşturan kişi OWNER olur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(user, request);
    }

    /** USER: üyesi olduğu projeler; ADMIN: tümü. {@code q} proje adında arar; {@code status} boşsa hepsi. */
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

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return projectService.get(user, id);
    }

    /** OWNER/ADMIN. Arşivden geri almak için {@code "status": "ACTIVE"}. */
    @PutMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(user, id, request);
    }

    /** OWNER/ADMIN: arşivler (204). {@code ?permanent=true} kalıcı silme Faz 4'te. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                        @RequestParam(defaultValue = "false") boolean permanent) {
        projectService.archive(user, id, permanent);
    }
}
