package com.vitalsafe.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vitalSafeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VitalSafe API")
                        .version("v1")
                        .description("Records and charts the readings you enter. It does not diagnose, treat or give "
                                + "medical advice and is not for emergencies."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    // Every endpoint except registration, login and token refresh needs the access token
    @Bean
    public OpenApiCustomizer protectedPathsNeedTheBearerToken() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/auth")) {
                    item.readOperations().forEach(operation ->
                            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
                }
            });
        };
    }
}