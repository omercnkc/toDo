package com.kafein.todo.task.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Filtreli liste {@link JpaSpecificationExecutor} ile ({@code task.query.TaskSpecifications}). */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Board: kolon (durum kodu) ve kolon içi sıraya göre; {@code (project_id, status, position)} indeksini kullanır. */
    List<Task> findByProjectIdOrderByStatusAscPositionAsc(long projectId);

    /** Kolondaki en büyük pozisyon; kolon boşsa -1. Yeni görev {@code max + 1}'e, yani kolonun sonuna eklenir. */
    @Query("select coalesce(max(t.position), -1) from Task t where t.projectId = :projectId and t.status = :status")
    int findMaxPosition(@Param("projectId") long projectId, @Param("status") TaskStatus status);

    /**
     * Kolondan bir görev çıkınca arkasındakileri bir öne alır (pozisyonlar boşluksuz kalır). Board kilidi altında
     * çağrılır. Toplu güncelleme: sıra değişikliği içerik düzenlemesi sayılmaz, {@code version}/{@code updated_at}
     * değişmez.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Task t set t.position = t.position - 1"
            + " where t.projectId = :projectId and t.status = :status and t.position > :position")
    int closeGap(@Param("projectId") long projectId, @Param("status") TaskStatus status,
                 @Param("position") int position);

    /**
     * Kolonda {@code [from, to]} aralığındaki görevleri {@code delta} kadar kaydırır (taşımada hedefe yer açmak
     * veya kolon içi taşımada aradakileri bir yana itmek için). Board kilidi altında çağrılır.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Task t set t.position = t.position + :delta"
            + " where t.projectId = :projectId and t.status = :status and t.position between :from and :to")
    int shift(@Param("projectId") long projectId, @Param("status") TaskStatus status,
              @Param("from") int from, @Param("to") int to, @Param("delta") int delta);

    /**
     * Dashboard: proje ve durum başına görev sayısı + gecikmiş sayısı (bitiş tarihi {@code today}'den önce ve
     * {@code DONE} değil). Tek sorgu, görevler belleğe alınmaz. {@code projectIds} boş olmamalı.
     */
    @Query("""
            select t.projectId as projectId, t.status as status, count(t) as total,
                   sum(case when t.dueDate < :today and t.status <> :done then 1 else 0 end) as overdue
              from Task t
             where t.projectId in :projectIds
             group by t.projectId, t.status""")
    List<StatusCount> countByProjectAndStatus(@Param("projectIds") Collection<Long> projectIds,
                                              @Param("today") LocalDate today, @Param("done") TaskStatus done);

    /** {@link #countByProjectAndStatus} tüm sistem için (ADMIN, {@code scope=all}). */
    @Query("""
            select t.projectId as projectId, t.status as status, count(t) as total,
                   sum(case when t.dueDate < :today and t.status <> :done then 1 else 0 end) as overdue
              from Task t
             group by t.projectId, t.status""")
    List<StatusCount> countAllByProjectAndStatus(@Param("today") LocalDate today, @Param("done") TaskStatus done);

    /**
     * Verilen projelerde kullanıcıya atanmış açık (DONE olmayan) görev sayısı ve bunlardan gecikmiş olanlar.
     * {@code (assignee_id, status)} indeksi. {@code projectIds} boş olmamalı.
     */
    @Query("""
            select count(t) as open,
                   coalesce(sum(case when t.dueDate < :today then 1 else 0 end), 0) as overdue
              from Task t
             where t.assigneeId = :userId and t.status <> :done and t.projectId in :projectIds""")
    AssignedCount countOpenAssigned(@Param("userId") long userId, @Param("projectIds") Collection<Long> projectIds,
                                    @Param("today") LocalDate today, @Param("done") TaskStatus done);

    /** {@link #countOpenAssigned} tüm sistemde (ADMIN, {@code scope=all}). */
    @Query("""
            select count(t) as open,
                   coalesce(sum(case when t.dueDate < :today then 1 else 0 end), 0) as overdue
              from Task t
             where t.assigneeId = :userId and t.status <> :done""")
    AssignedCount countAllOpenAssigned(@Param("userId") long userId, @Param("today") LocalDate today,
                                       @Param("done") TaskStatus done);

    interface StatusCount {
        long getProjectId();

        TaskStatus getStatus();

        long getTotal();

        long getOverdue();
    }

    interface AssignedCount {
        long getOpen();

        long getOverdue();
    }

    /** Projenin tüm görevleri (kalıcı silinen proje; outbox relay). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Task t where t.projectId = :projectId")
    int deleteByProject(@Param("projectId") long projectId);

    /**
     * Projeden çıkarılan kişinin o projedeki atamalarını boşaltır. Sıra kaydırmanın aksine içerik değişikliği
     * sayılır: normal düzenlemedeki gibi {@code version} artar ve {@code updated_at} güncellenir.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Task t set t.assigneeId = null, t.version = t.version + 1, t.updatedAt = :now"
            + " where t.projectId = :projectId and t.assigneeId = :userId")
    int unassignInProject(@Param("projectId") long projectId, @Param("userId") long userId,
                          @Param("now") Instant now);
}
