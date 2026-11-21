package com.kafein.todo.auth.authentication;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleTest {

    private static final String EMAIL = "kisi@ornek.com";

    /** Elle ilerletilen saat. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), clock);

    @Test
    void lockReleasesWhenWindowFromFirstFailureEnds() {
        throttle.recordFailure(EMAIL);
        clock.advance(Duration.ofMinutes(5));
        throttle.recordFailure(EMAIL);
        throttle.recordFailure(EMAIL);

        assertThatThrownBy(() -> throttle.checkAllowed(EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class, e -> {
                    // Pencere ilk hatadan başlar: 15 - 5 = 10 dk kaldı
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(600);
                    assertThat(e.getMessage()).contains("10 dakika");
                });

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> throttle.checkAllowed(EMAIL)).doesNotThrowAnyException();
        // Süre dolunca yeni pencere: tek hata kilitlemez
        throttle.recordFailure(EMAIL);
        assertThatCode(() -> throttle.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void failuresAfterExpiredWindowStartNewWindow() {
        throttle.recordFailure(EMAIL);
        throttle.recordFailure(EMAIL);
        clock.advance(Duration.ofMinutes(16));
        throttle.recordFailure(EMAIL);

        assertThatCode(() -> throttle.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void retryAfterRoundsUpToWholeMinuteInMessage() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(EMAIL);
        }
        clock.advance(Duration.ofMinutes(14).plusSeconds(30));

        assertThatThrownBy(() -> throttle.checkAllowed(EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class, e -> {
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(30);
                    assertThat(e.getMessage()).contains("1 dakika");
                });
    }

    @Test
    void resetClearsCounter() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(EMAIL);
        }
        throttle.reset(EMAIL);

        assertThatCode(() -> throttle.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }
}
