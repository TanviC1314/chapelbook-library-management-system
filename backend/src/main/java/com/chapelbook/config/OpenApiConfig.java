package com.chapelbook.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * Describes the API for OpenAPI / Swagger UI. The access rules shown for each operation are read from the same
 * {@code @PreAuthorize} annotations that enforce them, so the documentation cannot drift from the behaviour.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI chapelBookOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChapelBook API")
                        .version("v1")
                        .description("""
                                Library management and booking: catalogue, reservations, borrowing, fines and study seats.

                                **Signing in.** Call `POST /api/v1/auth/login`, copy the `accessToken`, press **Authorize** \
                                and paste it. Tokens last one hour.

                                **Errors** are RFC 9457 problem details (`application/problem+json`) with `status`, `title` \
                                and `detail`; validation errors add an `errors` list naming each bad field."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login"))
                        .addSchemas("ProblemDetail", problemDetailSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Adds the role rule from {@code @PreAuthorize} to each operation, and the error responses every call can have. */
    @Bean
    OperationCustomizer accessRulesAndErrors() {
        return (operation, handlerMethod) -> {
            boolean publicEndpoint = handlerMethod.getBeanType().getSimpleName().equals("AuthController");
            if (publicEndpoint) {
                operation.setSecurity(java.util.List.of()); // no token needed
            } else {
                operation.getResponses().addApiResponse("401", problem("Missing, invalid or expired token."));
                operation.getResponses().addApiResponse("403", problem("Your role is not allowed to do this."));
            }
            String rule = describeAccess(handlerMethod);
            String existing = operation.getDescription() == null ? "" : operation.getDescription() + "\n\n";
            operation.setDescription(existing + rule);
            return operation;
        };
    }

    private static String describeAccess(HandlerMethod method) {
        PreAuthorize annotation = method.getMethodAnnotation(PreAuthorize.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(PreAuthorize.class);
        }
        if (method.getBeanType().getSimpleName().equals("AuthController")) {
            return "**Access:** public, no token needed.";
        }
        if (annotation == null) {
            return "**Access:** any signed-in user.";
        }
        return "**Access:** `" + annotation.value() + "`";
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Conflict"))
                .addProperty("status", new Schema<Integer>().type("integer").example(409))
                .addProperty("detail", new StringSchema().example("This copy is already on loan."))
                .addProperty("instance", new StringSchema().example("/api/v1/loans"));
    }
}
