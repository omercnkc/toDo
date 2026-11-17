package com.kafein.todo.dashboard.summary;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.dashboard.client.ProjectDirectory;
import com.kafein.todo.dashboard.client.ProjectMembership;
import com.kafein.todo.dashboard.client.ProjectSummary;
import com.kafein.todo.dashboard.client.TaskDirectory;
import com.kafein.todo.dashboard.client.TaskStats;
import com.kafein.todo.dashboard.config.DashboardProperties;
import com.kafein.todo.dashboard.summary.DashboardSummary.AssignedToMe;
import com.kafein.todo.dashboard.summary.DashboardSummary.ProjectProgress;
import com.kafein.todo.dashboard.summary.DashboardSummary.TaskTotals;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.text.Collator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Dashboard özeti: proje listesi (project-service) ve görev sayıları (task-service) paralel alınıp birleştirilir.
 * <ul>
 *   <li>{@code mine}: üye olunan projeler. {@code all}: tüm sistem, yalnızca ADMIN (rol burada da kontrol edilir,
 *       aşağı servislere boşuna çağrı gitmesin; asıl karar onlarda).</li>
 *   <li>İki taraf ayrı anlarda okunduğu için ufak farklar olabilir: proje listesinde olmayan projenin sayıları
 *       (ör. kalıcı silinmiş, görevleri outbox ile henüz temizlenmemiş) <b>alınmaz</b>; sayısı gelmeyen proje
 *       sıfırla görünür. Toplamlar listedeki projelerden hesaplanır, yani her zaman kendi içinde tutarlıdır.</li>
 *   <li>Kısmi yanıt yok: bir taraf ulaşılamazsa 503. Sayısız proje listesi "görev yok" gibi okunup yanıltırdı.</li>
 *   <li>Önbellek (Caffeine): anahtar kullanıcı + rol + kapsam, süre {@code dashboard.cache-ttl}. Hatalar
 *       önbelleğe alınmaz. Rol anahtarda: ADMIN yetkisi alınan kullanıcı eski kapsamla yanıt almasın.</li>
 * </ul>
 * Paralel çağrılar sanal thread'lerde; Feign'in token ileten interceptor'ı token'ı istek bağlamından
 * ({@link RequestContextHolder}) okuduğu için bağlam çalışan thread'e taşınır.
 */
@Service
public class SummaryService {

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

    private record CacheKey(long userId, boolean admin, Scope scope) {
    }

    /** Projenin listede gösterilen hali (iki kapsamda ortak). */
    private record ProjectRef(long id, String name, String status, String myRole) {
    }

    private static final Comparator<ProjectProgress> ORDER;

    static {
        Collator turkish = Collator.getInstance(Locale.forLanguageTag("tr-TR"));
        turkish.setStrength(Collator.SECONDARY); // büyük/küçük harf duyarsız
        ORDER = Comparator.comparing((ProjectProgress p) -> "ARCHIVED".equals(p.status()))
                .thenComparing(ProjectProgress::name, Comparator.nullsLast(turkish))
                .thenComparingLong(ProjectProgress::id);
    }

    private final ProjectDirectory projects;
    private final TaskDirectory tasks;
    private final Clock clock;
    private final Cache<CacheKey, DashboardSummary> cache;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SummaryService(ProjectDirectory projects, TaskDirectory tasks, Clock clock, DashboardProperties properties) {
        this.projects = projects;
        this.tasks = tasks;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(properties.cacheTtl().isZero() ? 0 : 10_000)
                .build();
    }

    public DashboardSummary summary(AuthenticatedUser user, String scopeParam) {
        Scope scope = Scope.parse(scopeParam);
        if (scope == Scope.ALL && !user.isAdmin()) {
            throw ApiException.forbidden("STATS_SCOPE_FORBIDDEN",
                    "Tüm sistemin istatistiklerini yalnızca yöneticiler görebilir");
        }
        CacheKey key = new CacheKey(user.id(), user.isAdmin(), scope);
        DashboardSummary cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        DashboardSummary summary = scope == Scope.ALL ? compute(scope, this::allProjects) : compute(scope,
                () -> myProjects(user));
        cache.put(key, summary);
        return summary;
    }

    private List<ProjectRef> allProjects() {
        return projects.all().stream()
                .map((ProjectSummary p) -> new ProjectRef(p.id(), p.name(), p.status(), null))
                .toList();
    }

    private List<ProjectRef> myProjects(AuthenticatedUser user) {
        return projects.memberships(user.id()).stream()
                .map((ProjectMembership m) -> new ProjectRef(m.projectId(), m.projectName(), m.projectStatus(), m.role()))
                .toList();
    }

    private DashboardSummary compute(Scope scope, Supplier<List<ProjectRef>> projectSource) {
        CompletableFuture<List<ProjectRef>> projectsFuture = async(projectSource);
        CompletableFuture<TaskStats> statsFuture = async(() -> tasks.stats(scope.param()));
        List<ProjectRef> refs = join(projectsFuture);
        TaskStats stats = join(statsFuture);

        Map<Long, TaskStats.ProjectTaskStats> statsById = stats.projects().stream()
                .collect(Collectors.toMap(TaskStats.ProjectTaskStats::projectId, Function.identity(), (a, b) -> a));
        List<ProjectProgress> progress = new ArrayList<>(refs.size());
        long todo = 0, inProgress = 0, done = 0, overdue = 0;
        int archived = 0;
        for (ProjectRef ref : refs) {
            TaskStats.ProjectTaskStats s = statsById.getOrDefault(ref.id(),
                    new TaskStats.ProjectTaskStats(ref.id(), 0, 0, 0, 0, 0));
            long total = s.todo() + s.inProgress() + s.done();
            int percent = total == 0 ? 0 : (int) (s.done() * 100 / total);
            progress.add(new ProjectProgress(ref.id(), ref.name(), ref.status(), ref.myRole(), total, s.todo(),
                    s.inProgress(), s.done(), s.overdue(), percent));
            todo += s.todo();
            inProgress += s.inProgress();
            done += s.done();
            overdue += s.overdue();
            if ("ARCHIVED".equals(ref.status())) {
                archived++;
            }
        }
        progress.sort(ORDER);
        TaskStats.AssignedToMe mine = stats.assignedToMe();
        return new DashboardSummary(scope.param(), refs.size(), refs.size() - archived, archived,
                new TaskTotals(todo, inProgress, done), todo + inProgress + done, todo + inProgress, overdue,
                new AssignedToMe(mine.open(), mine.overdue()), List.copyOf(progress), clock.instant());
    }

    /** İstek bağlamını (Authorization header'ı) çalışan thread'e taşıyarak çalıştırır. */
    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return CompletableFuture.supplyAsync(() -> {
            RequestContextHolder.setRequestAttributes(attributes);
            try {
                return supplier.get();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }, executor);
    }

    /** Asıl hatayı (ör. 503 {@link ApiException}) sarmalamadan fırlatır. */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
