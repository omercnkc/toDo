package com.kafein.todo.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Uygulama tam ayağa kalkıyor mu: Flyway migration'ı uygulanır, Hibernate şemayı doğrular. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
