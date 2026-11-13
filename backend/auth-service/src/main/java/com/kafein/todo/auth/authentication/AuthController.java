package com.kafein.todo.auth.authentication;

import com.kafein.todo.auth.authentication.dto.AuthResponse;
import com.kafein.todo.auth.authentication.dto.LoginRequest;
import com.kafein.todo.auth.authentication.dto.RegisterRequest;
import com.kafein.todo.auth.authentication.dto.UserResponse;
import com.kafein.todo.auth.token.InvalidRefreshTokenException;
import com.kafein.todo.auth.token.RefreshTokenCookies;
import com.kafein.todo.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gateway'den {@code /api/auth/**} → burada {@code /auth/**} (StripPrefix=1). */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    /** Kayıt (rol: USER). Otomatik giriş yapılmaz; ardından /auth/login çağrılır. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Access token gövdede, refresh token httpOnly cookie'de döner. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /** Gövde yok; refresh token cookie'den okunur. Yeni access token + yeni cookie (rotasyon). */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    /** Refresh token iptal edilir, cookie silinir. Cookie yoksa da 204 (idempotent). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    /**
     * Geçersiz refresh token: 401 + cookie'yi sil. Tarayıcı işe yaramaz cookie'yi her refresh
     * denemesinde tekrar göndermesin. (Genel hata yöneticisinden önce bu çalışır.)
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ApiError.of(ex.getStatus().value(), ex.getError(), ex.getMessage(), request.getRequestURI()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.response());
    }
}
