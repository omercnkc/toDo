package com.kafein.todo.project.project.dto;

import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.project.Project;
import com.kafein.todo.project.project.ProjectStatus;

import java.time.Instant;

/**
 * Liste ve detayda aynı biçim.
 *
 * @param myRole    isteği yapanın rolü (OWNER/MEMBER); üye olmayan ADMIN için null
 * @param canManage düzenleme/arşiv/davet/üye çıkarma düğmeleri gösterilsin mi (OWNER veya ADMIN).
 *                  Arayüz kolaylığı içindir; her işlemde yetki sunucuda yeniden kontrol edilir.
 */
public record ProjectResponse(Long id, String name, String description, ProjectStatus status, Long ownerId,
                              MemberRole myRole, boolean canManage, long memberCount,
                              Instant createdAt, Instant updatedAt) {

    public static ProjectResponse from(Project project, MemberRole myRole, boolean admin, long memberCount) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getStatus(),
                project.getOwnerId(), myRole, admin || myRole == MemberRole.OWNER, memberCount,
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
