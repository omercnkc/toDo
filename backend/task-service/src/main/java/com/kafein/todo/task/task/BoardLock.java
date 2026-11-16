package com.kafein.todo.task.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proje bazlı board kilidi (bkz. {@code V2__create_task_board_locks.sql}). Kilit transaction bitene kadar tutulur;
 * aynı projede pozisyon değiştiren işlemler sıraya girer.
 */
@Component
public class BoardLock {

    private static final String LOCK_SQL =
            "INSERT INTO task_board_locks (project_id) VALUES (?) ON DUPLICATE KEY UPDATE project_id = project_id";

    private final JdbcTemplate jdbc;

    public BoardLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Transaction içinde çağrılmalı; dışında kilit hemen bırakılır ve hiçbir şeyi korumaz. */
    public void lock(long projectId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Board kilidi yalnızca transaction içinde alınabilir");
        }
        jdbc.update(LOCK_SQL, projectId);
    }
}
