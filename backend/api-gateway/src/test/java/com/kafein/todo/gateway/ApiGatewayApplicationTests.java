package com.kafein.todo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Testler çalışan bir Eureka sunucusuna ihtiyaç duymasın
@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
