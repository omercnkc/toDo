package com.kafein.todo.task.internal;

import com.kafein.todo.task.task.BoardLock;
import com.kafein.todo.task.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * project-service'teki olayların görevlere yansıması (outbox relay çağırır). İkisi de <b>idempotent</b>:
 * relay en az bir kez teslim eder, aynı çağrı tekrar gelirse sonuç değişmez.
 * Pozisyon/atama değiştirdikleri için board kilidi altında çalışır: kullanıcının o anki taşımasıyla çakışmaz.
 */
@Service
public class InternalTaskService {

    private static final Logger log = LoggerFactory.getLogger(InternalTaskService.class);

    private final TaskRepository tasks;
    private final BoardLock boardLock;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InternalTaskService(TaskRepository tasks, BoardLock boardLock, JdbcTemplate jdbc, Clock clock) {
        this.tasks = tasks;
        this.boardLock = boardLock;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Kalıcı silinen projenin tüm görevleri ve kilit satırı silinir. */
    @Transactional
    public int deleteProjectTasks(long projectId) {
        boardLock.lock(projectId);
        int deleted = tasks.deleteByProject(projectId);
        jdbc.update("DELETE FROM task_board_locks WHERE project_id = ?", projectId);
        log.info("Proje {} kalıcı silindi: {} görev temizlendi", projectId, deleted);
        return deleted;
    }

    /** Projeden çıkarılan kişinin o projedeki atamaları boşaltılır; görevler projede kalır. */
    @Transactional
    public int unassignMember(long projectId, long userId) {
        boardLock.lock(projectId);
        int updated = tasks.unassignInProject(projectId, userId, clock.instant());
        log.info("Kullanıcı {} proje {}'den çıkarıldı: {} görevin ataması kaldırıldı", userId, projectId, updated);
        return updated;
    }
}
