package com.kafein.todo.project.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Relay'i zamanlar. Testlerde {@code project.outbox.scheduling-enabled=false}: testler {@link OutboxRelay}'i
 * doğrudan, belirlenen anda çağırır.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "project.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxRelay relay;

    public OutboxScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    /**
     * Önceki çalışma bittikten 5 sn sonra. Dolu parti gelirse hemen bir sonrakine geçer (birikmiş kuyruk
     * beklemeden erir). Hata zamanlayıcıyı durdurmasın diye yakalanır.
     */
    @Scheduled(fixedDelayString = "${project.outbox.relay-delay:PT5S}", initialDelayString = "${project.outbox.relay-delay:PT5S}")
    public void relay() {
        try {
            while (relay.processBatch() == OutboxRelay.BATCH_SIZE) {
                // dolu parti: devam
            }
        } catch (RuntimeException e) {
            log.error("Outbox relay çalışması başarısız", e);
        }
    }

    /** Her gün 03:30 (sunucu saati). */
    @Scheduled(cron = "${project.outbox.purge-cron:0 30 3 * * *}")
    public void purge() {
        try {
            int deleted = relay.purgeSent();
            if (deleted > 0) {
                log.info("Outbox temizliği: {} eski SENT kaydı silindi", deleted);
            }
        } catch (RuntimeException e) {
            log.error("Outbox temizliği başarısız", e);
        }
    }
}
