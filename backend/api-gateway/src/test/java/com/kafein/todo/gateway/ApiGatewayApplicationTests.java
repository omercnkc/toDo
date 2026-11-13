package com.kafein.todo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Test profili: sabit JWT secret, Eureka kapalı (çalışan Eureka gerekmez)
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
