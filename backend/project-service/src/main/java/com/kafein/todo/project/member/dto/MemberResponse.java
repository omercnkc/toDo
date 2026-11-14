package com.kafein.todo.project.member.dto;

import com.kafein.todo.project.client.UserSummary;
import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;

import java.time.Instant;

/**
 * Üye listesi satırı. Ad/e-posta auth-service'ten gelir; ona ulaşılamazsa veya kullanıcı bulunamazsa
 * {@code email}/{@code fullName} null olur (liste yine döner, arayüz "Kullanıcı #id" gösterir).
 *
 * @param enabled kullanıcı hesabı aktif mi (pasif üyeler listede işaretlenir); bilinmiyorsa null
 */
public record MemberResponse(Long userId, String email, String fullName, Boolean enabled,
                             MemberRole role, Instant joinedAt) {

    public static MemberResponse from(ProjectMember member, UserSummary user) {
        return new MemberResponse(member.getUserId(),
                user != null ? user.email() : null,
                user != null ? user.fullName() : null,
                user != null ? user.enabled() : null,
                member.getRole(), member.getJoinedAt());
    }
}
