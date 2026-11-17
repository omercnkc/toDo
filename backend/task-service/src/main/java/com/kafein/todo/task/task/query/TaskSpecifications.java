package com.kafein.todo.task.task.query;

import com.kafein.todo.task.task.Task;
import com.kafein.todo.task.task.TaskStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link TaskFilter}'ı JPA Criteria sorgusuna çevirir.
 * <p>
 * Sıralama da burada kurulur ({@code Pageable} sırasız verilir): bitiş tarihi boş olan görevler, yön ne olursa
 * olsun sonda kalmalı; Spring Data'nın {@code Sort}'u Criteria sorgularında {@code NULLS LAST}'i uygulamıyor.
 * Sayım sorgusunda sıralama kurulmaz (Spring Data zaten temizler; gereksiz ifade de üretilmesin).
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /**
     * @param projectIds görevlerin projeleri, boş olmamalı (yetki çağıran tarafından kontrol edilmiş olmalı).
     *                   Tek projede {@code =}, birden fazlada {@code IN} üretilir.
     * @param today      "gecikmiş" hesabındaki bugün (uygulamanın saat dilimine göre)
     */
    public static Specification<Task> matching(Collection<Long> projectIds, TaskFilter filter, LocalDate today) {
        if (projectIds.isEmpty()) {
            throw new IllegalArgumentException("projectIds boş olamaz");
        }
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(projectIds.size() == 1
                    ? cb.equal(root.get("projectId"), projectIds.iterator().next())
                    : root.get("projectId").in(projectIds));

            if (!filter.statuses().isEmpty()) {
                where.add(root.get("status").in(filter.statuses()));
            }
            if (!filter.priorities().isEmpty()) {
                where.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.assignee() != null) {
                where.add(filter.assignee().unassigned()
                        ? cb.isNull(root.get("assigneeId"))
                        : cb.equal(root.get("assigneeId"), filter.assignee().userId()));
            }
            if (filter.likePattern() != null) {
                // Kolonlar utf8mb4_tr_0900_ai_ci (V3): Türkçe kurallarla büyük/küçük harf duyarsız, lower() gerekmez
                where.add(cb.or(
                        cb.like(root.get("title"), filter.likePattern(), '!'),
                        cb.like(root.get("description"), filter.likePattern(), '!')));
            }
            if (filter.dueFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueFrom()));
            }
            if (filter.dueTo() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueTo()));
            }
            if (filter.overdue()) {
                where.add(cb.lessThan(root.get("dueDate"), today));
                where.add(cb.notEqual(root.get("status"), TaskStatus.DONE));
            }

            if (!isCountQuery(query.getResultType())) {
                query.orderBy(orders(root, cb, filter.sort()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return resultType == Long.class || resultType == long.class;
    }

    private static List<Order> orders(Root<Task> root, CriteriaBuilder cb, List<TaskFilter.SortOrder> sort) {
        List<Order> orders = new ArrayList<>();
        for (TaskFilter.SortOrder order : sort) {
            Expression<?> path = root.get(order.field().param());
            if (order.field() == TaskSortField.DUE_DATE) {
                // Önce "tarihi var mı" (0 = var), sonra tarih: boş tarihler iki yönde de sonda
                orders.add(cb.asc(cb.selectCase().when(cb.isNull(path), 1).otherwise(0)));
            }
            orders.add(order.ascending() ? cb.asc(path) : cb.desc(path));
        }
        // Eşit değerlerde sayfalar arası tekrar/atlama olmasın: her zaman benzersiz bir son anahtar
        orders.add(cb.desc(root.get("id")));
        return orders;
    }
}
