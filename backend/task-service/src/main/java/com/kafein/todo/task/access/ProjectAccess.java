package com.kafein.todo.task.access;

import com.kafein.todo.task.client.ProjectRole;
import com.kafein.todo.task.task.Task;

/**
 * İsteği yapanın bir projedeki yetkisi (docs/07 yetki matrisi).
 *
 * @param role     projedeki rolü; üye olmayan ADMIN için null
 * @param admin    sistem ADMIN'i
 * @param archived proje arşivde mi (görevler okunur ama değiştirilemez)
 */
public record ProjectAccess(long projectId, long userId, ProjectRole role, boolean admin, boolean archived) {

    /** OWNER veya ADMIN: her görevi silebilir. */
    public boolean canManage() {
        return admin || role == ProjectRole.OWNER;
    }

    /** Görev silme: oluşturan (reporter), proje OWNER'ı veya ADMIN. */
    public boolean canDelete(Task task) {
        return canManage() || task.getReporterId() == userId;
    }
}
