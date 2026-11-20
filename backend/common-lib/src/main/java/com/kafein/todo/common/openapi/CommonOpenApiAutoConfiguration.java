package com.kafein.todo.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * springdoc kullanan <b>servlet</b> servislerine ortak OpenAPI ayarları (docs/06):
 * <ul>
 *   <li>Sunucu adresi {@code /api}: belge servisin kendi yollarını ({@code /tasks}) içerir, istekler gateway'den
 *       ({@code /api/tasks}) gider. Swagger UI'daki "Try it out" bu yüzden doğrudan gateway'e çağrı yapar.</li>
 *   <li>Varsayılan güvenlik: Bearer JWT. Herkese açık uçlar {@code @SecurityRequirements} (boş) ile işaretlenir.</li>
 *   <li>{@link TodoOpenApiCustomizer}: {@code /internal/**} belgeden çıkar, hata yanıtları ortak {@code ApiError}.</li>
 * </ul>
 * Gateway (WebFlux) için yüklenmez. Kayıt: {@code META-INF/spring/...AutoConfiguration.imports}
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenApiCustomizer.class)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class CommonOpenApiAutoConfiguration {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI todoOpenApi(Environment environment) {
        String service = environment.getProperty("spring.application.name", "todo");
        return new OpenAPI()
                .info(new Info()
                        .title(service + " API")
                        .version("v1")
                        .description("Görev Yöneticisi — " + service + ". Tüm istekler API Gateway üzerinden "
                                + "(`/api` öneki). Hata gövdesi her uçta `ApiError`; `error` alanı makine "
                                + "tarafından okunacak koddur (docs/06)."))
                .servers(List.of(new Server().url("/api").description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("`POST /api/auth/login` yanıtındaki `accessToken` (15 dk geçerli)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public TodoOpenApiCustomizer todoOpenApiCustomizer() {
        return new TodoOpenApiCustomizer();
    }
}
