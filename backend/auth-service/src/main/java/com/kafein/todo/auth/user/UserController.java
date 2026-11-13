package com.kafein.todo.auth.user;

import com.kafein.todo.auth.user.dto.AdminUpdateUserRequest;
import com.kafein.todo.auth.user.dto.ChangePasswordRequest;
import com.kafein.todo.auth.user.dto.UpdateProfileRequest;
import com.kafein.todo.auth.user.dto.UserDetailResponse;
import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway'den {@code /api/users/**} → burada {@code /users/**}. Hepsi giriş gerektirir.
 * Parametre kısıtları (@Min/@Max) Spring'in yerleşik metot doğrulamasıyla 400'e dönüşür.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDetailResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getProfile(user.id());
    }

    /** Yalnızca ad güncellenir (e-posta ve rol buradan değişmez). */
    @PutMapping("/me")
    public UserDetailResponse updateMe(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user.id(), request);
    }

    /** 204; tüm oturumlar kapanır, istemci yeniden giriş ekranına yönlenmeli. */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
    }

    /** USER: üye davet etmek için arama (q ≥ 3, yalnızca aktifler, özet). ADMIN: kullanıcı yönetimi listesi. */
    @GetMapping
    public PageResponse<?> search(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 veya daha büyük olmalı") int page,
                                  @RequestParam(defaultValue = "20")
                                  @Min(value = 1, message = "en az 1 olmalı")
                                  @Max(value = 100, message = "en fazla 100 olabilir") int size) {
        return userService.search(user, q, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse getById(@PathVariable long id) {
        return userService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                     @RequestBody AdminUpdateUserRequest request) {
        return userService.adminUpdate(admin, id, request);
    }
}
