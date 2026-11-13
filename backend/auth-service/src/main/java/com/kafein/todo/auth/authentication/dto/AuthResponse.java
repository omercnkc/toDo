package com.kafein.todo.auth.authentication.dto;

/**
 * Giriş/yenileme yanıtı (docs/06). Refresh token gövdede DEĞİL, httpOnly cookie'de döner.
 *
 * @param expiresIn access token ömrü (saniye)
 */
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
