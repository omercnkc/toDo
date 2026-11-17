package com.kafein.todo.dashboard.client;

import java.util.List;

/** task-service {@code TaskStatsResponse} karşılığı (docs/06 "İstatistik"). */
public record TaskStats(String scope, List<ProjectTaskStats> projects, AssignedToMe assignedToMe) {

    public record ProjectTaskStats(long projectId, long todo, long inProgress, long done, long overdue, long total) {
    }

    public record AssignedToMe(long open, long overdue) {
    }
}
