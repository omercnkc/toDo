package com.kafein.todo.project.client;

/** auth-service {@code InternalUserResponse} karşılığı. */
public record UserSummary(Long id, String email, String fullName, boolean enabled) {
}
