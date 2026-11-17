package com.kafein.todo.task.stats;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.access.TaskAccessService;
import com.kafein.todo.task.config.TaskProperties;
import com.kafein.todo.task.stats.TaskStatsResponse.AssignedToMe;
import com.kafein.todo.task.stats.TaskStatsResponse.ProjectTaskStats;
import com.kafein.todo.task.task.TaskRepository;
import com.kafein.todo.task.task.TaskRepository.AssignedCount;
import com.kafein.todo.task.task.TaskRepository.StatusCount;
import com.kafein.todo.task.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dashboard sayıları (docs/04 "dashboard-service": tüm görevleri çekmeden toplu istatistik).
 * <p>
 * {@code mine}: kullanıcının üye olduğu projeler (5-B'deki kapsamla aynı; arşivdekiler dahil, ayırt etmek
 * dashboard'un işi). {@code all}: tüm sistem, yalnızca ADMIN (docs/07 yetki matrisi "Dashboard: tüm veri").
 * Üyelik project-service'e transaction dışında sorulur, sayımlar iki gruplu sorguyla yapılır.
 */
@Service
public class TaskStatsService {

    public enum Scope {
        MINE, ALL;

        static Scope parse(String value) {
            if (value == null || value.isBlank()) {
                return MINE;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Doğrulama hatası")
                        .withField("scope", "mine veya all olmalı");
            }
        }

        String param() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final TaskRepository tasks;
    private final TaskAccessService access;
    private final Clock clock;
    private final ZoneId zone;

    public TaskStatsService(TaskRepository tasks, TaskAccessService access, Clock clock, TaskProperties properties) {
        this.tasks = tasks;
        this.access = access;
        this.clock = clock;
        this.zone = properties.timeZone();
    }

    public TaskStatsResponse stats(AuthenticatedUser user, String scopeParam) {
        Scope scope = Scope.parse(scopeParam);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        if (scope == Scope.ALL) {
            if (!user.isAdmin()) {
                throw ApiException.forbidden("STATS_SCOPE_FORBIDDEN",
                        "Tüm sistemin istatistiklerini yalnızca yöneticiler görebilir");
            }
            Map<Long, Counter> byProject = new TreeMap<>();
            add(byProject, tasks.countAllByProjectAndStatus(today, TaskStatus.DONE));
            return response(scope, byProject, tasks.countAllOpenAssigned(user.id(), today, TaskStatus.DONE));
        }

        Set<Long> projectIds = access.requireMemberships(user).keySet();
        if (projectIds.isEmpty()) {
            return new TaskStatsResponse(scope.param(), List.of(), new AssignedToMe(0, 0));
        }
        Map<Long, Counter> byProject = new TreeMap<>();
        projectIds.forEach(id -> byProject.put(id, new Counter())); // görevi olmayan projeler de sıfırla görünsün
        add(byProject, tasks.countByProjectAndStatus(projectIds, today, TaskStatus.DONE));
        return response(scope, byProject,
                tasks.countOpenAssigned(user.id(), projectIds, today, TaskStatus.DONE));
    }

    private static void add(Map<Long, Counter> byProject, List<StatusCount> rows) {
        for (StatusCount row : rows) {
            Counter counter = byProject.computeIfAbsent(row.getProjectId(), id -> new Counter());
            switch (row.getStatus()) {
                case TODO -> counter.todo += row.getTotal();
                case IN_PROGRESS -> counter.inProgress += row.getTotal();
                case DONE -> counter.done += row.getTotal();
            }
            counter.overdue += row.getOverdue();
        }
    }

    private static TaskStatsResponse response(Scope scope, Map<Long, Counter> byProject, AssignedCount assigned) {
        List<ProjectTaskStats> projects = new ArrayList<>(byProject.size());
        byProject.forEach((id, c) -> projects.add(new ProjectTaskStats(id, c.todo, c.inProgress, c.done, c.overdue,
                c.todo + c.inProgress + c.done)));
        return new TaskStatsResponse(scope.param(), projects, new AssignedToMe(assigned.getOpen(), assigned.getOverdue()));
    }

    private static final class Counter {
        long todo;
        long inProgress;
        long done;
        long overdue;
    }
}
