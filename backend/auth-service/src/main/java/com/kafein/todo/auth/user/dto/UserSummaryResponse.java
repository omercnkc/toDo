package com.kafein.todo.auth.user.dto;

import com.kafein.todo.auth.user.User;

/** USER'ın aramada görebildiği en az bilgi (docs/06: yalnızca id/ad/e-posta). */
public record UserSummaryResponse(Long id, String email, String fullName) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName());
    }
}
