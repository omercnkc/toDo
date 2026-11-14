package com.kafein.todo.auth.user;

import com.kafein.todo.auth.authentication.validation.PasswordPolicyValidator;
import com.kafein.todo.auth.token.RefreshTokenService;
import com.kafein.todo.auth.user.dto.AdminUpdateUserRequest;
import com.kafein.todo.auth.user.dto.ChangePasswordRequest;
import com.kafein.todo.auth.user.dto.InternalUserResponse;
import com.kafein.todo.auth.user.dto.UpdateProfileRequest;
import com.kafein.todo.auth.user.dto.UserDetailResponse;
import com.kafein.todo.auth.user.dto.UserSummaryResponse;
import com.kafein.todo.common.dto.PageResponse;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.persistence.LikePatterns;
import com.kafein.todo.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

@Service
public class UserService {

    /** USER aramasında en az bu kadar karakter: tek harfle tüm kullanıcılar listelenemesin. */
    static final int MIN_SEARCH_LENGTH = 3;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Sort SEARCH_SORT = Sort.by("fullName").ascending().and(Sort.by("id").ascending());

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
    }

    // ---------- kendi profili ----------

    @Transactional(readOnly = true)
    public UserDetailResponse getProfile(long userId) {
        return UserDetailResponse.from(findOrThrow(userId));
    }

    @Transactional
    public UserDetailResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = findOrThrow(userId);
        user.changeFullName(request.fullName());
        return UserDetailResponse.from(users.saveAndFlush(user));
    }

    /**
     * Şifre değişince kullanıcının tüm refresh token'ları iptal edilir: şifresi çalınmış birinin
     * açık oturumları da kapanır. Bu cihazda da yeniden giriş gerekir (frontend login'e yönlendirir).
     */
    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = findOrThrow(userId);
        boolean currentMatches = PasswordPolicyValidator.fitsBcrypt(request.currentPassword())
                && passwordEncoder.matches(request.currentPassword(), user.getPasswordHash());
        if (!currentMatches) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Mevcut şifre hatalı")
                    .withField("currentPassword", "mevcut şifre hatalı");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "Yeni şifre mevcut şifreden farklı olmalı")
                    .withField("newPassword", "mevcut şifreden farklı olmalı");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        int revoked = refreshTokens.revokeAll(user.getId());
        log.info("Kullanıcı {} şifresini değiştirdi; {} oturum kapatıldı", user.getId(), revoked);
    }

    // ---------- arama ----------

    /**
     * ADMIN: tüm kullanıcılar (pasifler dahil), {@code q} isteğe bağlı, detaylı yanıt.
     * USER: {@code q} en az 3 karakter, yalnızca aktif kullanıcılar, yalnızca id/ad/e-posta (docs/06).
     */
    @Transactional(readOnly = true)
    public PageResponse<?> search(AuthenticatedUser caller, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, SEARCH_SORT);
        if (caller.isAdmin()) {
            return toPage(users.search(LikePatterns.contains(q), false, pageable), UserDetailResponse::from);
        }
        if (q == null || q.trim().length() < MIN_SEARCH_LENGTH) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Doğrulama hatası")
                    .withField("q", "en az " + MIN_SEARCH_LENGTH + " karakter olmalı");
        }
        return toPage(users.search(LikePatterns.contains(q), true, pageable), UserSummaryResponse::from);
    }

    // ---------- ADMIN ----------

    @Transactional(readOnly = true)
    public UserDetailResponse getById(long id) {
        return UserDetailResponse.from(findOrThrow(id));
    }

    /**
     * Rol / aktiflik güncellemesi. ADMIN kendi rolünü değiştiremez ve kendini pasifleştiremez
     * (tek ADMIN kendini sistemden kilitlemesin). Pasifleştirilen kullanıcının tüm oturumları kapatılır;
     * elindeki access token en fazla 15 dk daha geçerli kalır.
     */
    @Transactional
    public UserDetailResponse adminUpdate(AuthenticatedUser caller, long id, AdminUpdateUserRequest request) {
        if (request.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "En az bir alan (role, enabled) gönderilmeli");
        }
        User user = findOrThrow(id);
        if (user.getId().equals(caller.id())) {
            throw ApiException.conflict("SELF_MODIFICATION_NOT_ALLOWED",
                    "Kendi rolünüzü veya hesap durumunuzu değiştiremezsiniz");
        }
        if (request.role() != null) {
            user.changeRole(request.role());
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.changeEnabled(request.enabled());
            if (!request.enabled()) {
                int revoked = refreshTokens.revokeAll(user.getId());
                log.info("ADMIN {} kullanıcı {}'i pasifleştirdi; {} oturum kapatıldı", caller.id(), id, revoked);
            }
        }
        return UserDetailResponse.from(users.saveAndFlush(user));
    }

    // ---------- servisler arası ----------

    @Transactional(readOnly = true)
    public InternalUserResponse getInternal(long id) {
        return InternalUserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<InternalUserResponse> lookup(List<Long> ids) {
        return users.findAllByIdInOrderByIdAsc(new LinkedHashSet<>(ids)).stream()
                .map(InternalUserResponse::from)
                .toList();
    }

    private User findOrThrow(long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Kullanıcı bulunamadı"));
    }

    private static <T> PageResponse<T> toPage(Page<User> page, Function<User, T> mapper) {
        return PageResponse.of(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
