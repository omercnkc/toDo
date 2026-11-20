package com.kafein.todo.auth.user;

import com.kafein.todo.auth.user.dto.AdminUpdateUserRequest;
import com.kafein.todo.auth.user.dto.ChangePasswordRequest;
import com.kafein.todo.auth.user.dto.UpdateProfileRequest;
import com.kafein.todo.auth.user.dto.UserDetailResponse;
import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Kullanıcılar", description = "Profil, şifre, kullanıcı arama ve ADMIN kullanıcı yönetimi.")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Profilim")
    @ApiResponse(responseCode = "200", description = "Profil")
    @GetMapping("/me")
    public UserDetailResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getProfile(user.id());
    }

    /** Yalnızca ad güncellenir (e-posta ve rol buradan değişmez). */
    @Operation(summary = "Adımı güncelle",
            description = "Yalnızca ad; e-posta ve rol buradan değişmez.")
    @ApiResponse(responseCode = "200", description = "Güncel profil")
    @PutMapping("/me")
    public UserDetailResponse updateMe(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user.id(), request);
    }

    /** 204; tüm oturumlar kapanır, istemci yeniden giriş ekranına yönlenmeli. */
    @Operation(summary = "Şifre değiştir",
            description = "Tüm oturumlar kapanır (bu cihaz dahil); istemci yeniden giriş yapmalı.")
    @ApiResponse(responseCode = "204", description = "Şifre değişti")
    @ApiResponse(responseCode = "400", description = "INVALID_CURRENT_PASSWORD (`fieldErrors[currentPassword]`) / SAME_PASSWORD (`fieldErrors[newPassword]`) / VALIDATION_ERROR")
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
    }

    /** USER: üye davet etmek için arama (q ≥ 3, yalnızca aktifler, özet). ADMIN: kullanıcı yönetimi listesi. */
    @Operation(summary = "Kullanıcı ara / listele",
            description = "USER: üye davet etmek için arama — `q` en az 3 karakter, yalnızca aktif kullanıcılar, yalnızca `{id, email, fullName}`. ADMIN: kullanıcı yönetimi — `q` isteğe bağlı, pasifler dahil, ayrıntılı. Ad veya e-postada geçen metin (büyük/küçük harf duyarsız, `%` `_` joker sayılmaz); sıralama ad, id; `size` 1–100.")
    @ApiResponse(responseCode = "200", description = "Sayfalı liste")
    @GetMapping
    public PageResponse<?> search(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 veya daha büyük olmalı") int page,
                                  @RequestParam(defaultValue = "20")
                                  @Min(value = 1, message = "en az 1 olmalı")
                                  @Max(value = 100, message = "en fazla 100 olabilir") int size) {
        return userService.search(user, q, page, size);
    }

    @Operation(summary = "Kullanıcı ayrıntısı (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Kullanıcı")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN — yalnızca ADMIN")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse getById(@PathVariable long id) {
        return userService.getById(id);
    }

    @Operation(summary = "Rol / aktiflik değiştir (ADMIN)",
            description = "`{role?, enabled?}`; gönderilmeyen alan değişmez. Pasifleştirilen kullanıcının tüm oturumları kapanır.")
    @ApiResponse(responseCode = "200", description = "Güncel kullanıcı")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN — yalnızca ADMIN")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "SELF_MODIFICATION_NOT_ALLOWED — ADMIN kendi rolünü/durumunu değiştiremez")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                     @RequestBody AdminUpdateUserRequest request) {
        return userService.adminUpdate(admin, id, request);
    }
}
