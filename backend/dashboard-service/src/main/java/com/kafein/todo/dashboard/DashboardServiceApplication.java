package com.kafein.todo.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Dashboard özeti (docs/04 "dashboard-service"): veritabanı yok; project-service ve task-service'ten Feign ile
 * toplar. Kullanıcının token'ı iletilir (common-lib {@code FeignAuthForwardingInterceptor}), yetki karar veren
 * servislerde kalır.
 */
@SpringBootApplication
@EnableFeignClients
public class DashboardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardServiceApplication.class, args);
    }
}
