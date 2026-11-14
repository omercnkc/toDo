package com.kafein.todo.project.access;

import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.project.Project;

/**
 * Bir kullanıcının bir projedeki yetkisi.
 *
 * @param myRole kullanıcının üyelik rolü; ADMIN üye değilse null
 * @param admin  sistem ADMIN'i mi (tüm projeleri görür ve yönetir)
 */
public record ProjectAccess(Project project, MemberRole myRole, boolean admin) {

    /** Düzenleme, arşivleme, davet ve üye çıkarma: OWNER veya ADMIN (docs/07). */
    public boolean canManage() {
        return admin || myRole == MemberRole.OWNER;
    }
}
