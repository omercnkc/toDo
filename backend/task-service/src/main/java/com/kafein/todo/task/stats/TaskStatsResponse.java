package com.kafein.todo.task.stats;

import java.util.List;

/**
 * {@code GET /tasks/stats} yanıtı (docs/06). Görevler değil, yalnızca sayılar.
 *
 * @param scope        {@code mine} (üye olunan projeler) veya {@code all} (tüm sistem, yalnızca ADMIN)
 * @param projects     proje başına sayılar, proje id'sine göre. {@code mine}'da görevi olmayan projeler de sıfırlarla
 *                     yer alır; {@code all}'da yalnızca görevi olan projeler (task-service tüm projeleri bilmez)
 * @param assignedToMe istekte bulunana atanmış açık görevler (kapsam içinde)
 */
public record TaskStatsResponse(String scope, List<ProjectTaskStats> projects, AssignedToMe assignedToMe) {

    /**
     * @param overdue bitiş tarihi bugünden önce ve {@code DONE} olmayanlar
     * @param total   {@code todo + inProgress + done}
     */
    public record ProjectTaskStats(long projectId, long todo, long inProgress, long done, long overdue, long total) {

        public static ProjectTaskStats empty(long projectId) {
            return new ProjectTaskStats(projectId, 0, 0, 0, 0, 0);
        }
    }

    /** @param open DONE olmayanlar; @param overdue bunlardan gecikmiş olanlar */
    public record AssignedToMe(long open, long overdue) {
    }
}
