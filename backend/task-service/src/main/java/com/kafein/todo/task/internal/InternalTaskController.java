package com.kafein.todo.task.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servisler arası uçlar (docs/06 "Dahili Uçlar"). Gateway {@code /internal/**} yolunu route etmez; ayrıca
 * yalnızca servis token'ı kabul edilir ({@code SecurityConfig}). Çağıran: project-service outbox relay'i.
 * Her ikisi de idempotenttir ve her durumda 204 döner.
 */
@RestController
@RequestMapping("/internal/tasks/by-project/{projectId}")
public class InternalTaskController {

    private final InternalTaskService internalTasks;

    public InternalTaskController(InternalTaskService internalTasks) {
        this.internalTasks = internalTasks;
    }

    /** Proje kalıcı silindi: tüm görevleri sil. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProjectTasks(@PathVariable long projectId) {
        internalTasks.deleteProjectTasks(projectId);
    }

    /** Kullanıcı projeden çıkarıldı: o projedeki atamalarını kaldır. */
    @DeleteMapping("/assignees/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignMember(@PathVariable long projectId, @PathVariable long userId) {
        internalTasks.unassignMember(projectId, userId);
    }
}
