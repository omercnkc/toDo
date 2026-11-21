package com.kafein.todo.auth.authentication;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * E-posta başına hatalı giriş sınırı (docs/07 "Giriş denemesi"): {@code lock-duration} içinde
 * {@code max-failures} hatalı denemeden sonra o e-postayla giriş, pencerenin sonuna kadar
 * {@code 429 TOO_MANY_LOGIN_ATTEMPTS} alır. Kontrol şifre doğrulamasından (BCrypt) <b>önce</b> yapılır.
 * <ul>
 *   <li>Kayıtlı olmayan e-postalara da aynı kural uygulanır: yanıttan e-postanın kayıtlı olup olmadığı anlaşılmaz.</li>
 *   <li>Başarılı giriş sayacı sıfırlar. Pencere ilk hatalı denemeden başlar (sabit pencere).</li>
 *   <li>Bedeli: saldırgan bir kullanıcının e-postasıyla bilerek hatalı deneme yaparak onu en fazla
 *       {@code lock-duration} kadar dışarıda bırakabilir; süre bu yüzden kısa tutulur.</li>
 *   <li>Sayaç servis belleğinde (tek örnek); birden fazla örnekte paylaşılmaz.</li>
 * </ul>
 * IP başına kaba sınır ayrıca gateway'de ({@code AuthRateLimitFilter}).
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private record Window(Instant start, int failures) {
    }

    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;
    /** Bellek sınırı: pencereyi aşan kayıtlar zaten geçersiz, Caffeine onları atar. */
    private final Cache<String, Window> windows;

    public LoginThrottle(@Value("${auth.login.max-failures:5}") int maxFailures,
                         @Value("${auth.login.lock-duration:15m}") Duration lockDuration,
                         Clock clock) {
        if (maxFailures < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalStateException("auth.login.max-failures ≥ 1 ve auth.login.lock-duration > 0 olmalı");
        }
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(lockDuration)
                .maximumSize(100_000)
                .build();
    }

    /** @throws TooManyLoginAttemptsException e-posta şu an kilitliyse */
    public void checkAllowed(String normalizedEmail) {
        Window window = windows.getIfPresent(normalizedEmail);
        if (window == null || window.failures() < maxFailures) {
            return;
        }
        Duration left = Duration.between(clock.instant(), window.start().plus(lockDuration));
        if (left.isNegative() || left.isZero()) {
            windows.invalidate(normalizedEmail);
            return;
        }
        throw new TooManyLoginAttemptsException(left);
    }

    public void recordFailure(String normalizedEmail) {
        Instant now = clock.instant();
        Window updated = windows.asMap().compute(normalizedEmail, (key, current) ->
                current == null || !now.isBefore(current.start().plus(lockDuration))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.failures() + 1));
        if (updated.failures() == maxFailures) {
            // E-postanın kendisi loglanmaz (kişisel veri); yalnızca olay
            log.warn("Hatalı giriş sınırına ulaşıldı ({} deneme); e-posta {} süreyle kilitlendi", maxFailures, lockDuration);
        }
    }

    public void reset(String normalizedEmail) {
        windows.invalidate(normalizedEmail);
    }
}
