package com.kafein.todo.task.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Board: kolon (durum kodu) ve kolon içi sıraya göre; {@code (project_id, status, position)} indeksini kullanır. */
    List<Task> findByProjectIdOrderByStatusAscPositionAsc(long projectId);

    /** Kolondaki en büyük pozisyon; kolon boşsa -1. Yeni görev {@code max + 1}'e, yani kolonun sonuna eklenir. */
    @Query("select coalesce(max(t.position), -1) from Task t where t.projectId = :projectId and t.status = :status")
    int findMaxPosition(@Param("projectId") long projectId, @Param("status") TaskStatus status);
}
