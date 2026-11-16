package com.kafein.todo.task.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Board: kolon (durum kodu) ve kolon içi sıraya göre; {@code (project_id, status, position)} indeksini kullanır. */
    List<Task> findByProjectIdOrderByStatusAscPositionAsc(long projectId);

    /** Projenin görevleri, sayfalı (Faz 5'te filtreli listeye dönüşecek). */
    Page<Task> findByProjectId(long projectId, Pageable pageable);

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
}
