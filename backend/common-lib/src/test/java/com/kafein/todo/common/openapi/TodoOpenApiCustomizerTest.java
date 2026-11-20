package com.kafein.todo.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoOpenApiCustomizerTest {

    private final TodoOpenApiCustomizer customizer = new TodoOpenApiCustomizer();

    @Test
    void removesInternalPathsAndSchemasOnlyTheyUse() {
        OpenAPI api = new OpenAPI()
                .paths(new Paths()
                        .addPathItem("/tasks", new PathItem().get(okReturning("TaskResponse")))
                        .addPathItem("/internal/users/lookup", new PathItem().post(okReturning("LookupResponse"))))
                .components(new Components()
                        // TaskResponse → Person (iç içe referans korunmalı)
                        .addSchemas("TaskResponse", new ObjectSchema().addProperty("owner", ref("Person")))
                        .addSchemas("Person", new ObjectSchema().addProperty("name", new StringSchema()))
                        .addSchemas("LookupResponse", new ObjectSchema()));

        customizer.customise(api);

        assertThat(api.getPaths()).containsOnlyKeys("/tasks");
        assertThat(api.getComponents().getSchemas())
                .containsKeys("TaskResponse", "Person", "ApiError", "FieldError")
                .doesNotContainKey("LookupResponse");
    }

    @Test
    void protectedOperationGets401AndErrorsUseApiError() {
        Operation op = okReturning("TaskResponse")
                .requestBody(new RequestBody())
                .addParametersItem(new Parameter().in("path").name("id"));
        // springdoc @ApiResponse'a metodun dönüş tipini yazar; hata gövdesi ApiError olmalı
        op.getResponses().addApiResponse("409", new ApiResponse().description("PROJECT_ARCHIVED")
                .content(json(ref("TaskResponse"))));
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/tasks/{id}", new PathItem().put(op)));

        customizer.customise(api);

        ApiResponses responses = api.getPaths().get("/tasks/{id}").getPut().getResponses();
        assertThat(responses.keySet()).containsExactly("200", "400", "401", "409");
        assertThat(responses.get("409").getDescription()).isEqualTo("PROJECT_ARCHIVED");
        for (String code : List.of("400", "401", "409")) {
            assertThat(responses.get(code).getContent().get("application/json").getSchema().get$ref())
                    .isEqualTo("#/components/schemas/ApiError");
        }
        assertThat(responses.get("200").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/TaskResponse");
    }

    @Test
    void openOperationGetsNo401AndCookieParameterIsNotInput() {
        Operation refresh = okReturning("TaskResponse")
                .security(List.of()) // @SecurityRequirements → herkese açık
                .addParametersItem(new Parameter().in("cookie").name("refresh_token"));
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/auth/refresh", new PathItem().post(refresh)));

        customizer.customise(api);

        assertThat(api.getPaths().get("/auth/refresh").getPost().getResponses().keySet()).containsExactly("200");
    }

    private static Operation okReturning(String schema) {
        return new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK").content(json(ref(schema)))));
    }

    private static Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }
}
