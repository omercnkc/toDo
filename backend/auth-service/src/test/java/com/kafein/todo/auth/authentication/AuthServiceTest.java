package com.kafein.todo.auth.authentication;

import com.kafein.todo.auth.authentication.dto.LoginRequest;
import com.kafein.todo.auth.authentication.dto.RegisterRequest;
import com.kafein.todo.auth.authentication.dto.UserResponse;
import com.kafein.todo.auth.token.RefreshTokenService;
import com.kafein.todo.auth.user.User;
import com.kafein.todo.auth.user.UserRepository;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String PASSWORD = "Gizli.Sifre1";

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private final JwtService jwtService =
            new JwtService("test-secret-test-secret-test-secret-123", Duration.ofMinutes(15));

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, jwtService, refreshTokens);
    }

    // ---------- register ----------

    @Test
    void kayitSifreyiHashlerVeRoluUserYapar() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 5L));

        UserResponse response = service.register(new RegisterRequest(" Ali@Ornek.com ", PASSWORD, "Ali Veli"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$");
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        assertThat(response).isEqualTo(new UserResponse(5L, "ali@ornek.com", "Ali Veli", Role.USER));
    }

    @Test
    void kayitliEpostaIle409() {
        when(users.existsByEmail("ali@ornek.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ALI@ornek.com", PASSWORD, "Ali")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getError()).isEqualTo("EMAIL_ALREADY_EXISTS");
                });
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void esZamanliKayittaUniqueIhlali409Olur() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("ali@ornek.com", PASSWORD, "Ali")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getError()).isEqualTo("EMAIL_ALREADY_EXISTS"));
    }

    // ---------- login ----------

    @Test
    void basariliGiristeAccessVeRefreshTokenDoner() {
        User user = existingUser("ali@ornek.com", true);
        when(refreshTokens.issue(user)).thenReturn("ham-refresh-token");

        AuthService.LoginResult result = service.login(new LoginRequest(" ALI@ornek.com", PASSWORD));

        assertThat(result.refreshToken()).isEqualTo("ham-refresh-token");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.response().user().email()).isEqualTo("ali@ornek.com");
        assertThat(jwtService.parseAccessToken(result.response().accessToken()))
                .isEqualTo(new AuthenticatedUser(7L, "ali@ornek.com", Role.USER));
    }

    @Test
    void yanlisSifre401VeRefreshTokenUretilmez() {
        existingUser("ali@ornek.com", true);

        assertInvalidCredentials(new LoginRequest("ali@ornek.com", "yanlis-sifre"));
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void olmayanEpostaAyniHatayiVerirVeYineBcryptCalisir() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertInvalidCredentials(new LoginRequest("yok@ornek.com", PASSWORD));
        // Zamanlama farkı olmasın diye sahte hash ile karşılaştırma yapılmış olmalı
        verify(passwordEncoder).matches(eq(PASSWORD), anyString());
    }

    @Test
    void yetmisIkiBaytiAsanSifreBcrypteVerilmedenReddedilir() {
        existingUser("ali@ornek.com", true);

        assertInvalidCredentials(new LoginRequest("ali@ornek.com", "a".repeat(100)));
        verify(passwordEncoder, never()).matches(eq("a".repeat(100)), anyString());
    }

    @Test
    void pasifHesap403() {
        existingUser("ali@ornek.com", false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ali@ornek.com", PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getError()).isEqualTo("ACCOUNT_DISABLED");
                });
        verify(refreshTokens, never()).issue(any());
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getError()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    private User existingUser(String email, boolean enabled) {
        User user = withId(new User(email, passwordEncoder.encode(PASSWORD), "Ali Veli", Role.USER), 7L);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
