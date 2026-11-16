package com.kafein.todo.project.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Entegrasyon testlerinde zamanlayıcı kapalı olduğu için {@code @Scheduled} ifadeleri (gecikme, cron) orada
 * doğrulanmaz; burada gerçek {@code @EnableScheduling} ile bean'in kurulabildiği kontrol edilir.
 */
class OutboxSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxScheduler.class)
            .withBean(OutboxRelay.class, () -> mock(OutboxRelay.class));

    @Test
    void varsayilanAyarlarlaZamanlayiciKurulur() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxScheduler.class);
        });
    }

    @Test
    void ayarlaKapatilabilir() {
        runner.withPropertyValues("project.outbox.scheduling-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OutboxScheduler.class));
    }
}
