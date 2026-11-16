package com.kafein.todo.project.outbox;

import com.kafein.todo.project.client.TaskClient;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox relay (docs/04 "Proje Kalıcı Silme – Outbox"). Bir parti:
 * <ol>
 *   <li><b>Sahiplenme</b> (kısa transaction): zamanı gelmiş en fazla {@value #BATCH_SIZE} kayıt
 *       {@code FOR UPDATE SKIP LOCKED} ile kilitlenir ve {@code next_attempt_at} {@link #LEASE} kadar ileri alınır.
 *       Böylece task-service çağrıları sırasında veritabanı kilidi tutulmaz; başka bir örnek bu kayıtları almaz;
 *       relay işlem ortasında çökerse kayıtlar kira bitince yeniden denenir (en az bir kez teslim).</li>
 *   <li><b>Teslim</b> (transaction dışında): her kayıt için task-service'in idempotent dahili ucu çağrılır.</li>
 *   <li><b>Sonuç</b> (kayıt başına kısa transaction): başarı → {@code SENT}; hata → {@code attempts+1},
 *       {@code next_attempt_at = now + min(5 sn × 2^attempts, 10 dk)}.</li>
 * </ol>
 * Deneme sınırı ({@value #MAX_ATTEMPTS}) yalnızca task-service'in <i>yanıt verip hata döndüğü</i> durumlarda
 * uygulanır ({@code FAILED} + ERROR log). Servise <i>ulaşılamıyorsa</i> (kapalı, Eureka'da örnek yok, zaman aşımı)
 * sınır yoktur, 10 dk aralıkla denenmeye devam eder: "task-service kapalıyken silinen projenin görevleri servis
 * açılınca temizlenir" (Faz 4 bitiş kriteri) kesinti ne kadar sürerse sürsün sağlanır.
 */
@Component
public class OutboxRelay {

    static final int BATCH_SIZE = 20;
    static final int MAX_ATTEMPTS = 10;
    static final Duration BASE_DELAY = Duration.ofSeconds(5);
    static final Duration MAX_DELAY = Duration.ofMinutes(10);
    static final Duration LEASE = Duration.ofMinutes(5);
    static final Duration SENT_RETENTION = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository events;
    private final TaskClient taskClient;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository events, TaskClient taskClient,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.events = events;
        this.taskClient = taskClient;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Bir parti işler; işlenen kayıt sayısını döner (0 = bekleyen yok). */
    public int processBatch() {
        List<OutboxEvent> claimed = claim();
        for (OutboxEvent event : claimed) {
            deliver(event);
        }
        return claimed.size();
    }

    /** 7 günden eski SENT kayıtlarını siler; silinen sayısını döner. */
    public int purgeSent() {
        Instant before = clock.instant().minus(SENT_RETENTION);
        Integer deleted = tx.execute(status -> events.deleteProcessedBefore(OutboxStatus.SENT, before));
        return deleted == null ? 0 : deleted;
    }

    private List<OutboxEvent> claim() {
        return tx.execute(status -> {
            Instant now = clock.instant();
            List<OutboxEvent> due = events.lockDueEvents(now, BATCH_SIZE);
            due.forEach(event -> event.claimUntil(now.plus(LEASE)));
            return due;
        });
    }

    private void deliver(OutboxEvent event) {
        try {
            send(event);
            tx.executeWithoutResult(status -> events.findById(event.getId())
                    .ifPresent(e -> e.markSent(clock.instant())));
            log.info("Outbox {} ({} proje {}) task-service'e iletildi", event.getId(), event.getEventType(),
                    event.getAggregateId());
        } catch (RuntimeException e) {
            recordFailure(event, e);
        }
    }

    private void send(OutboxEvent event) {
        long projectId = event.getAggregateId();
        switch (event.getEventType()) {
            case PROJECT_DELETED -> taskClient.deleteProjectTasks(projectId);
            case MEMBER_REMOVED -> taskClient.unassignMember(projectId, event.payloadUserId());
        }
    }

    private void recordFailure(OutboxEvent event, RuntimeException error) {
        boolean unreachable = isUnreachable(error);
        int attempts = event.getAttempts() + 1;
        boolean giveUp = !unreachable && attempts >= MAX_ATTEMPTS;
        Instant now = clock.instant();
        Instant retryAt = now.plus(backoff(attempts));
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();

        tx.executeWithoutResult(status -> events.findById(event.getId())
                .ifPresent(e -> e.markAttemptFailed(message, retryAt, giveUp, now)));
        if (giveUp) {
            log.error("Outbox {} ({} proje {}) {} denemeden sonra FAILED: {}", event.getId(), event.getEventType(),
                    event.getAggregateId(), attempts, message);
        } else {
            log.warn("Outbox {} ({} proje {}) iletilemedi (deneme {}{}), {} tekrar denenecek: {}", event.getId(),
                    event.getEventType(), event.getAggregateId(), attempts, unreachable ? ", servis ulaşılamaz" : "",
                    retryAt, message);
        }
    }

    /** {@code min(5 sn × 2^attempts, 10 dk)}; taşmaya karşı üs sınırlı. */
    static Duration backoff(int attempts) {
        int exponent = Math.min(attempts, 20);
        Duration delay = BASE_DELAY.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    /**
     * Servise ulaşılamadı mı (yanıt yok): bağlantı reddi/zaman aşımı ({@link RetryableException}) veya
     * LoadBalancer'ın Eureka'da örnek bulamaması (503). Bu durumlarda deneme sınırı uygulanmaz.
     */
    static boolean isUnreachable(RuntimeException error) {
        if (error instanceof RetryableException) {
            return true;
        }
        return error instanceof FeignException feign && (feign.status() == 503 || feign.status() < 0);
    }
}
