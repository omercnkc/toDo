package com.kafein.todo.common.openapi;

import com.kafein.todo.common.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Belgeyi tüm servislerde aynı kurallarla tamamlar:
 * <ul>
 *   <li>{@code /internal/**} yolları (ve yalnızca onların kullandığı şemalar) silinir: gateway'de route'ları
 *       yok, dışarıdan çağrılamazlar (docs/06).</li>
 *   <li>Korumalı her uca 401, gövdesi ya da parametresi olan her uca 400 eklenir (uç kendisi tanımlamadıysa).</li>
 *   <li>Tüm 4xx/5xx yanıtlarının gövdesi {@code ApiError} şemasıdır; uçlarda yalnızca
 *       {@code @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS")} yazmak yeterli.</li>
 * </ul>
 */
public class TodoOpenApiCustomizer implements OpenApiCustomizer {

    static final String API_ERROR = "ApiError";
    private static final Pattern SCHEMA_REF = Pattern.compile("#/components/schemas/([^\"]+)");
    private static final String JSON = "application/json";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() != null) {
            openApi.getPaths().keySet().removeIf(path -> path.equals("/internal") || path.startsWith("/internal/"));
        }
        addErrorSchemas(openApi);
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(item -> item.readOperations().forEach(this::completeResponses));
        removeUnusedSchemas(openApi);
    }

    /**
     * Yalnızca silinen {@code /internal/**} uçlarının kullandığı DTO'lar (ör. {@code LookupRequest}) belgede
     * kalmasın: yollardan başlayıp şemalar arası referansları izleyerek ulaşılamayan şemalar silinir.
     */
    @SuppressWarnings("rawtypes")
    private void removeUnusedSchemas(OpenAPI openApi) {
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        if (schemas == null) {
            return;
        }
        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(refs(openApi.getPaths()));
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (reachable.add(name) && schemas.containsKey(name)) {
                pending.addAll(refs(schemas.get(name)));
            }
        }
        schemas.keySet().retainAll(reachable);
    }

    private static Set<String> refs(Object node) {
        Set<String> names = new HashSet<>();
        Matcher matcher = SCHEMA_REF.matcher(Json.pretty(node));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private void addErrorSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        // ApiError ve iç kaydı FieldError; şemalar ada göre sıralı kalsın diye TreeMap
        @SuppressWarnings("rawtypes")
        Map<String, Schema> merged = new TreeMap<>(ModelConverters.getInstance().readAll(ApiError.class));
        if (openApi.getComponents().getSchemas() != null) {
            merged.putAll(openApi.getComponents().getSchemas());
        }
        openApi.getComponents().setSchemas(merged);
    }

    private void completeResponses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        ApiResponses responses = operation.getResponses();
        // security == [] → uç @SecurityRequirements ile herkese açık işaretlenmiş
        boolean open = operation.getSecurity() != null && operation.getSecurity().isEmpty();
        if (!open) {
            responses.putIfAbsent("401", new ApiResponse()
                    .description("UNAUTHORIZED — token yok, geçersiz ya da süresi dolmuş"));
        }
        // Çerez parametresi (refresh_token) sayılmaz: eksik/geçersiz çerez 400 değil 401 verir
        boolean hasInput = operation.getRequestBody() != null || (operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> !"cookie".equals(p.getIn())));
        if (hasInput) {
            responses.putIfAbsent("400", new ApiResponse()
                    .description("VALIDATION_ERROR (`fieldErrors` ile) ya da BAD_REQUEST (okunamayan gövde / parametre)"));
        }
        // Tüm hatalar ApiError döner. springdoc @ApiResponse'lara metodun dönüş tipini yazdığı için üzerine yazılır.
        responses.forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5")) {
                response.setContent(new Content().addMediaType(JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR))));
            }
        });
        // Kodlar artan sırada görünsün (200, 201, 400, 401, ...)
        ApiResponses sorted = new ApiResponses();
        new TreeMap<>(responses).forEach(sorted::addApiResponse);
        if (responses.getExtensions() != null) {
            sorted.setExtensions(responses.getExtensions());
        }
        operation.setResponses(sorted);
    }
}
