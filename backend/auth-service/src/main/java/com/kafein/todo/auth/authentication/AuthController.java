package com.kafein.todo.auth.authentication;

import com.kafein.todo.auth.authentication.dto.AuthResponse;
import com.kafein.todo.auth.authentication.dto.LoginRequest;
import com.kafein.todo.auth.authentication.dto.RegisterRequest;
import com.kafein.todo.auth.authentication.dto.UserResponse;
import com.kafein.todo.auth.token.InvalidRefreshTokenException;
import com.kafein.todo.auth.token.RefreshTokenCookies;
import com.kafein.todo.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Kimlik", description = "Kayıt, giriş, access token yenileme ve çıkış. Bu uçlar token istemez.")
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
    @SecurityRequirements
    @Operation(summary = "Kayıt ol",
            description = "Rol USER. Otomatik giriş yapılmaz; ardından `/auth/login` çağrılır.")
    @ApiResponse(responseCode = "201", description = "Kullanıcı oluşturuldu")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — e-posta biçimi, şifre 8 karakter–72 bayt, ad boş/uzun")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS — e-posta kayıtlı (büyük/küçük harf duyarsız; `fieldErrors[email]`)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Access token gövdede, refresh token httpOnly cookie'de döner. */
    @SecurityRequirements
    @Operation(summary = "Giriş yap",
            description = "Access token gövdede döner (15 dk). Refresh token yalnızca `refresh_token` httpOnly çerezinde (`SameSite=Strict; Path=/api/auth`, 7 gün); hiçbir zaman gövdede dönmez.")
    @ApiResponse(responseCode = "200", description = "Giriş başarılı; `Set-Cookie: refresh_token`")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS — e-posta veya şifre hatalı (ikisi ayırt edilmez)")
    @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED — şifre doğru ama hesap pasif")
    @ApiResponse(responseCode = "429", description = "TOO_MANY_LOGIN_ATTEMPTS — bu e-postayla 15 dk içinde 5 hatalı deneme; `Retry-After` saniye. TOO_MANY_REQUESTS — aynı IP'den çok fazla istek (gateway)")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /** Gövde yok; refresh token cookie'den okunur. Yeni access token + yeni cookie (rotasyon). */
    @SecurityRequirements
    @Operation(summary = "Access token yenile",
            description = "Gövde yok; refresh token çerezden okunur, yeni access token + yeni çerez döner (rotasyon). Tarayıcıdan, uygulamanın kendi adresinden çağrılır: gateway `Origin` başlığını denetler, bu yüzden Swagger UI'dan (farklı origin) çalışmaz.")
    @ApiResponse(responseCode = "200", description = "Yeni access token; `Set-Cookie: refresh_token` (yenilenmiş)")
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN — çerez yok, geçersiz, iptal edilmiş ya da süresi dolmuş (çerez silinir)")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN — izin verilmeyen Origin (gateway)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    /** Refresh token iptal edilir, cookie silinir. Cookie yoksa da 204 (idempotent). */
    @SecurityRequirements
    @Operation(summary = "Çıkış yap",
            description = "Refresh token iptal edilir, çerez silinir (`Max-Age=0`). Çerez yoksa da 204 (idempotent).")
    @ApiResponse(responseCode = "204", description = "Çıkış yapıldı")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN — izin verilmeyen Origin (gateway)")
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

    /** 429 + {@code Retry-After} (saniye): istemci ne zaman tekrar deneyebileceğini bilir. */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(ApiError.of(ex.getStatus().value(), ex.getError(), ex.getMessage(), request.getRequestURI()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.response());
    }
}
