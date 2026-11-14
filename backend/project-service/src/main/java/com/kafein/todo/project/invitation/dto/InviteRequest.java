package com.kafein.todo.project.invitation.dto;

import jakarta.validation.constraints.NotNull;

/** Davet edilecek kullanıcı (arayüz kullanıcı aramasından id'yi alır: {@code GET /api/users?q=}). */
public record InviteRequest(@NotNull(message = "boş olamaz") Long userId) {
}
