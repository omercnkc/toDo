package com.kafein.todo.dashboard;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Testlerin ortak tabanı: gerçek güvenlik zinciri ve gerçek Feign çağrıları; project-service ve task-service yerine
 * {@link StubServer}. Servis adresleri Eureka yerine Spring Cloud'un basit keşif istemcisinden. Tüm alt sınıflar
 * aynı Spring context'ini ve sahte sunucuları paylaşır (JVM sonuna kadar açık); her test başında sıfırlanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class DashboardTestSupport {

    protected static final String SECRET = "test-secret-test-secret-test-secret-123"; // application-test.yml
    protected static final JwtService JWT = new JwtService(SECRET, Duration.ofMinutes(15));
    protected static final StubServer PROJECT_SERVICE = new StubServer();
    protected static final StubServer TASK_SERVICE = new StubServer();
    /** Önbellek kullanıcı başına: her test kendi kullanıcılarıyla çalışsın. */
    private static final AtomicLong IDS = new AtomicLong(100);

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.project-service[0].uri", PROJECT_SERVICE::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[0].uri", TASK_SERVICE::uri);
    }

    @Autowired
    protected MockMvc mvc;

    @BeforeEach
    void resetServers() {
        PROJECT_SERVICE.reset();
        TASK_SERVICE.reset();
    }

    protected static AuthenticatedUser newUser(Role role) {
        long id = IDS.incrementAndGet();
        return new AuthenticatedUser(id, "kullanici" + id + "@ornek.com", role);
    }

    protected static String bearer(AuthenticatedUser user) {
        return "Bearer " + JWT.generateAccessToken(user);
    }
}
