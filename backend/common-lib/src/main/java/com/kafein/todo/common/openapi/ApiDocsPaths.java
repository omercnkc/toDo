package com.kafein.todo.common.openapi;

/**
 * Servislerin OpenAPI belgesinin yolu. Güvenlik zincirlerinde herkese açılır; gateway bunları
 * {@code /api/docs/{servis}} adresinden Swagger UI'a sunar. {@code API_DOCS_ENABLED=false} ile kapatılır.
 * <p>
 * springdoc'a bağımlı değildir: springdoc'u olmayan modüller de güvenlik ayarında kullanabilir.
 */
public final class ApiDocsPaths {

    public static final String[] PATTERNS = {"/v3/api-docs", "/v3/api-docs/**"};

    private ApiDocsPaths() {
    }
}
