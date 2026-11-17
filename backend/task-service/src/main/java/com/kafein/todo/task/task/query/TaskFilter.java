package com.kafein.todo.task.task.query;

import com.kafein.todo.common.error.ApiError;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.persistence.LikePatterns;
import com.kafein.todo.task.task.TaskPriority;
import com.kafein.todo.task.task.TaskStatus;
import com.kafein.todo.task.task.dto.TaskLimits;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Doğrulanmış liste filtresi. Boş alan = o filtre yok. Filtreler birbirine VE ile, çok değerli bir filtrenin
 * değerleri VEYA ile bağlanır ({@code status=TODO,DONE&priority=HIGH} → "TODO veya DONE" ve "HIGH").
 *
 * @param statuses    boşsa tüm durumlar
 * @param priorities  boşsa tüm öncelikler
 * @param assignee    atanan kişi filtresi; null = filtre yok
 * @param likePattern {@code q}'dan üretilmiş, jokerleri kaçışlı {@code LIKE} deseni (başlık veya açıklama)
 * @param dueFrom     bitiş tarihi bu günden önce olmayanlar (dahil); tarihi olmayan görevler elenir
 * @param dueTo       bitiş tarihi bu günden sonra olmayanlar (dahil); tarihi olmayan görevler elenir
 * @param overdue     yalnızca gecikmişler: bitiş tarihi bugünden önce ve durum {@code DONE} değil
 * @param sort        en az bir öğe; sonuna ayrıca {@code id} eklenir (eşit değerlerde kararlı sayfalama)
 */
public record TaskFilter(Set<TaskStatus> statuses, Set<TaskPriority> priorities, AssigneeFilter assignee,
                         String likePattern, LocalDate dueFrom, LocalDate dueTo, boolean overdue,
                         List<SortOrder> sort) {

    /** Varsayılan sıralama: en yeniler önce (Faz 4'teki davranış). */
    public static final List<SortOrder> DEFAULT_SORT = List.of(new SortOrder(TaskSortField.CREATED_AT, false));

    static final String UNASSIGNED = "none";

    /** {@code assigneeId=none} → atanmamışlar; sayı → o kişiye atananlar. */
    public record AssigneeFilter(Long userId) {
        public boolean unassigned() {
            return userId == null;
        }
    }

    public record SortOrder(TaskSortField field, boolean ascending) {
    }

    /** Tüm parametreleri kontrol eder; hatalar tek seferde 400 {@code VALIDATION_ERROR} + {@code fieldErrors}. */
    public static TaskFilter parse(TaskListParams params) {
        List<ApiError.FieldError> errors = new ArrayList<>();

        Set<TaskStatus> statuses = parseEnums("status", params.status(), TaskStatus.class, errors);
        Set<TaskPriority> priorities = parseEnums("priority", params.priority(), TaskPriority.class, errors);
        AssigneeFilter assignee = parseAssignee(params.assigneeId(), errors);
        String likePattern = parseQuery(params.q(), errors);
        LocalDate dueFrom = parseDate("dueFrom", params.dueFrom(), errors);
        LocalDate dueTo = parseDate("dueTo", params.dueTo(), errors);
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            errors.add(new ApiError.FieldError("dueTo", "başlangıç tarihinden önce olamaz"));
        }
        boolean overdue = parseBoolean("overdue", params.overdue(), errors);
        List<SortOrder> sort = parseSort(params.sort(), errors);

        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Doğrulama hatası", errors);
        }
        return new TaskFilter(statuses, priorities, assignee, likePattern, dueFrom, dueTo, overdue, sort);
    }

    /** Virgüllü ve tekrarlı değerleri tek listede toplar; boş parçalar atılır. */
    private static List<String> tokens(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .flatMap(v -> Arrays.stream(v.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static <E extends Enum<E>> Set<E> parseEnums(String field, List<String> values, Class<E> type,
                                                         List<ApiError.FieldError> errors) {
        Set<E> result = EnumSet.noneOf(type);
        for (String token : tokens(values)) {
            try {
                result.add(Enum.valueOf(type, token.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                String allowed = String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
                errors.add(new ApiError.FieldError(field, "geçersiz değer: " + token + " (izin verilenler: " + allowed + ")"));
                return result;
            }
        }
        return result;
    }

    private static AssigneeFilter parseAssignee(String value, List<ApiError.FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (UNASSIGNED.equalsIgnoreCase(trimmed)) {
            return new AssigneeFilter(null);
        }
        Long id = parseOrNull(trimmed, Long::valueOf);
        if (id == null || id <= 0) {
            errors.add(new ApiError.FieldError("assigneeId", "pozitif bir kullanıcı id'si veya \"none\" olmalı"));
            return null;
        }
        return new AssigneeFilter(id);
    }

    private static String parseQuery(String q, List<ApiError.FieldError> errors) {
        if (q != null && q.trim().length() > TaskLimits.TITLE_MAX) {
            errors.add(new ApiError.FieldError("q", "en fazla " + TaskLimits.TITLE_MAX + " karakter olabilir"));
            return null;
        }
        return LikePatterns.contains(q);
    }

    private static LocalDate parseDate(String field, String value, List<ApiError.FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.add(new ApiError.FieldError(field, "YYYY-AA-GG biçiminde geçerli bir tarih olmalı"));
            return null;
        }
    }

    private static boolean parseBoolean(String field, String value, List<ApiError.FieldError> errors) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                errors.add(new ApiError.FieldError(field, "true veya false olmalı"));
                yield false;
            }
        };
    }

    /**
     * Parçaları sırayla okur: her alan adından sonra isteğe bağlı {@code asc}/{@code desc} gelir (yoksa artan).
     * {@code sort=dueDate,asc&sort=title} ve {@code sort=dueDate,asc,title} aynı sonucu verir.
     */
    private static List<SortOrder> parseSort(List<String> values, List<ApiError.FieldError> errors) {
        List<String> tokens = tokens(values);
        if (tokens.isEmpty()) {
            return DEFAULT_SORT;
        }
        List<SortOrder> result = new ArrayList<>();
        Set<TaskSortField> seen = EnumSet.noneOf(TaskSortField.class);
        for (int i = 0; i < tokens.size(); i++) {
            String name = tokens.get(i);
            TaskSortField field = TaskSortField.fromParam(name).orElse(null);
            if (field == null) {
                errors.add(new ApiError.FieldError("sort",
                        "geçersiz alan: " + name + " (izin verilenler: " + TaskSortField.allowed() + ")"));
                return DEFAULT_SORT;
            }
            if (!seen.add(field)) {
                errors.add(new ApiError.FieldError("sort", "aynı alan birden fazla kez verilemez: " + name));
                return DEFAULT_SORT;
            }
            boolean ascending = true;
            if (i + 1 < tokens.size()) {
                String next = tokens.get(i + 1).toLowerCase(Locale.ROOT);
                if (next.equals("asc") || next.equals("desc")) {
                    ascending = next.equals("asc");
                    i++;
                }
            }
            result.add(new SortOrder(field, ascending));
        }
        return List.copyOf(result);
    }

    private static <T> T parseOrNull(String value, Function<String, T> parser) {
        try {
            return parser.apply(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
