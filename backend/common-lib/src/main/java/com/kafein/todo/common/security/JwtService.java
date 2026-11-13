package com.kafein.todo.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Access token üretimi ve doğrulaması (HS256, bkz. docs/07-security.md).
 * <p>
 * Claim'ler: {@code sub} (kullanıcı id), {@code email}, {@code role}, {@code iat}, {@code exp}.
 * Spring'e bağımlı değildir; her servis kendi yapılandırmasında bean olarak oluşturur.
 * Thread-safe'tir.
 */
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

    /** HS256 için RFC 7518 alt sınırı: 256 bit. */
    static final int MIN_SECRET_BYTES = 32;

    private static final String ALGORITHM = Jwts.SIG.HS256.getId();

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(String secret, Duration accessTokenTtl) {
        this(secret, accessTokenTtl, Clock.systemUTC());
    }

    public JwtService(String secret, Duration accessTokenTtl, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret en az " + MIN_SECRET_BYTES + " bayt olmalı (JWT_SECRET ortam değişkeni)");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Access token süresi pozitif olmalı");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(user.id()))
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                // Algoritma açıkça verilir: verilmezse jjwt anahtar uzunluğuna göre HS384/HS512 seçebilir
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token'ı doğrular ve kullanıcıyı döner.
     *
     * @throws InvalidTokenException imza, süre, algoritma veya claim'ler geçersizse
     */
    public AuthenticatedUser parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token boş");
        }
        try {
            // parseSignedClaims: imzasız ("alg": "none") token'ları da reddeder
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new InvalidTokenException("Beklenmeyen imza algoritması: " + jws.getHeader().getAlgorithm());
            }
            return toUser(jws.getPayload());
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token süresi dolmuş", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Geçersiz token", e);
        }
    }

    /** Access token ömrü; login yanıtındaki {@code expiresIn} alanı için. */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    private static AuthenticatedUser toUser(Claims claims) {
        // exp zorunlu: jjwt exp'siz token'ı süresiz kabul eder
        if (claims.getExpiration() == null) {
            throw new InvalidTokenException("exp claim'i yok");
        }
        String subject = claims.getSubject();
        String email = claims.get(CLAIM_EMAIL, String.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        if (subject == null || email == null || role == null) {
            throw new InvalidTokenException("Zorunlu claim eksik");
        }
        // Sayı olmayan sub veya bilinmeyen rol IllegalArgumentException fırlatır → "Geçersiz token"
        return new AuthenticatedUser(Long.parseLong(subject), email, Role.valueOf(role));
    }
}
