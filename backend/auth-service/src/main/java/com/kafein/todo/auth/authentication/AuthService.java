package com.kafein.todo.auth.authentication;

import com.kafein.todo.auth.authentication.dto.AuthResponse;
import com.kafein.todo.auth.authentication.dto.LoginRequest;
import com.kafein.todo.auth.authentication.dto.RegisterRequest;
import com.kafein.todo.auth.authentication.dto.UserResponse;
import com.kafein.todo.auth.authentication.validation.PasswordPolicyValidator;
import com.kafein.todo.auth.token.RefreshTokenService;
import com.kafein.todo.auth.user.User;
import com.kafein.todo.auth.user.UserRepository;
import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    /**
     * Var olmayan e-postayla girişte de bir BCrypt karşılaştırması yapılır. Yapılmasaydı
     * "kullanıcı yok" yanıtı belirgin şekilde hızlı döner ve hangi e-postaların kayıtlı olduğu
     * yanıt süresinden anlaşılırdı.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        try {
            User user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
                    request.fullName(), Role.USER));
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail ile kayıt arasında aynı e-postayla eşzamanlı kayıt: UNIQUE kısıtı yakalar
            throw emailTaken();
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        // 72 bayttan uzun şifre kayıtta kabul edilmediği için zaten eşleşemez (BCrypt'e de verilmez)
        boolean passwordMatches = PasswordPolicyValidator.fitsBcrypt(request.password())
                && passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordMatches) {
            // E-posta mı şifre mi yanlış, ayırt edilmez (kullanıcı keşfini zorlaştırır)
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "E-posta veya şifre hatalı");
        }
        User user = found.get();
        if (!user.isEnabled()) {
            // Şifre doğru olduğu için hesabın durumunu söylemek bilgi sızdırmaz
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Hesabınız pasif durumda");
        }

        String accessToken = jwtService.generateAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole()));
        String refreshToken = refreshTokens.issue(user);
        AuthResponse response = new AuthResponse(accessToken, jwtService.getAccessTokenTtl().toSeconds(),
                UserResponse.from(user));
        return new LoginResult(response, refreshToken);
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_ALREADY_EXISTS", "Bu e-posta adresi zaten kayıtlı");
    }

    /** Controller'a dönen sonuç: gövde + cookie'ye yazılacak ham refresh token. */
    public record LoginResult(AuthResponse response, String refreshToken) {

        @Override
        public String toString() {
            return "LoginResult[user=" + response.user().email() + "]";
        }
    }
}
