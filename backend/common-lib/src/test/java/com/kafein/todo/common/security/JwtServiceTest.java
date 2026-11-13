package com.kafein.todo.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123"; // 39 bayt
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final AuthenticatedUser USER = new AuthenticatedUser(42L, "ali@ornek.com", Role.ADMIN);

    private final JwtService service = new JwtService(SECRET, TTL, clockAt(NOW));

    @Test
    void uretilenTokenGeriCozulur() {
        String token = service.generateAccessToken(USER);

        assertThat(service.parseAccessToken(token)).isEqualTo(USER);
    }

    @Test
    void tokenClaimleriDokumandakiGibidir() {
        String token = service.generateAccessToken(USER);

        Jws<Claims> jws = Jwts.parser().verifyWith(key(SECRET)).clock(() -> Date.from(NOW)).build()
                .parseSignedClaims(token);
        Claims claims = jws.getPayload();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("ali@ornek.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void suresiDolmusTokenReddedilir() {
        String token = service.generateAccessToken(USER);
        JwtService later = new JwtService(SECRET, TTL, clockAt(NOW.plus(TTL).plusSeconds(1)));

        assertThatThrownBy(() -> later.parseAccessToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("süresi dolmuş");
    }

    @Test
    void farkliAnahtarlaImzalanmisTokenReddedilir() {
        JwtService other = new JwtService("baska-secret-baska-secret-baska-secret", TTL, clockAt(NOW));
        String token = other.generateAccessToken(USER);

        assertThatThrownBy(() -> service.parseAccessToken(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void icerigiDegistirilmisTokenReddedilir() {
        String token = service.generateAccessToken(new AuthenticatedUser(42L, "ali@ornek.com", Role.USER));
        String[] parts = token.split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"42\",\"email\":\"ali@ornek.com\",\"role\":\"ADMIN\","
                + "\"iat\":" + NOW.getEpochSecond() + ",\"exp\":" + NOW.plus(TTL).getEpochSecond() + "}");
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.parseAccessToken(forged)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void imzasizTokenReddedilir() {
        String unsigned = base64Url("{\"alg\":\"none\"}") + "."
                + base64Url("{\"sub\":\"42\",\"email\":\"a@b.com\",\"role\":\"ADMIN\",\"exp\":"
                + NOW.plus(TTL).getEpochSecond() + "}") + ".";

        assertThatThrownBy(() -> service.parseAccessToken(unsigned)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void ayniAnahtarlaFarkliAlgoritmaReddedilir() {
        // 64+ baytlık secret ile HS512 imzası da doğrulanabilir; yalnızca HS256 kabul edilmeli
        String longSecret = "x".repeat(64);
        JwtService longService = new JwtService(longSecret, TTL, clockAt(NOW));
        String hs512 = Jwts.builder()
                .subject("42").claim("email", "a@b.com").claim("role", "ADMIN")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plus(TTL)))
                .signWith(key(longSecret), Jwts.SIG.HS512)
                .compact();

        assertThatThrownBy(() -> longService.parseAccessToken(hs512))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("algoritma");
    }

    @Test
    void expClaimiOlmayanTokenReddedilir() {
        String noExp = Jwts.builder()
                .subject("42").claim("email", "a@b.com").claim("role", "USER")
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service.parseAccessToken(noExp)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void bilinmeyenRolReddedilir() {
        assertThatThrownBy(() -> service.parseAccessToken(signed("42", "ROOT")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void sayiOlmayanSubjectReddedilir() {
        assertThatThrownBy(() -> service.parseAccessToken(signed("project-service", "USER")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void bosVeyaBozukTokenReddedilir() {
        assertThatThrownBy(() -> service.parseAccessToken(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.parseAccessToken("  ")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.parseAccessToken("abc.def")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void kisaSecretKabulEdilmez() {
        assertThatThrownBy(() -> new JwtService("x".repeat(31), TTL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bayt");
        assertThatThrownBy(() -> new JwtService(null, TTL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sureSifirVeyaNegatifOlamaz() {
        assertThatThrownBy(() -> new JwtService(SECRET, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String signed(String subject, String role) {
        return Jwts.builder()
                .subject(subject).claim("email", "a@b.com").claim("role", role)
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plus(TTL)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
