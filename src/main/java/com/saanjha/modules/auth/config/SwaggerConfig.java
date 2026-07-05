package com.saanjha.modules.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server; // ADD THIS IMPORT
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                // CRITICAL FIX: Forces Swagger to use the current network IP/Domain dynamically
                .addServersItem(new Server().url("/").description("Current Network Server"))

                .info(new Info()
                        .title("Saanjha 2.0 API")
                        .version("v2.0")
                        .description("Modular Monolith Backend API Documentation")
                        .contact(new Contact().name("Saanjha Engineering")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT Access Token here.")));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1. Authentication")
                .pathsToMatch("/v1/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("2. User Profile")
                .pathsToMatch("/v1/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi projectApi() {
        return GroupedOpenApi.builder()
                .group("3. Projects")
                .pathsToMatch("/v1/projects/**")
                .pathsToExclude("/v1/projects/*/applications/**", "/v1/projects/*/invitations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi applicationApi() {
        return GroupedOpenApi.builder()
                .group("4. Applications")
                .pathsToMatch("/v1/applications/**", "/v1/projects/*/applications/**")
                .build();
    }

    @Bean
    public GroupedOpenApi invitationApi() {
        return GroupedOpenApi.builder()
                .group("5. Invitations")
                .pathsToMatch("/v1/invitations/**", "/v1/projects/*/invitations/**")
                .build();
    }
}