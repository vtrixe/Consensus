package com.example.consensus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
    }

    @Bean
    public OpenAPI consensusOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Consensus — Trade Break & Reconciliation API")
                        .description("""
                                Multi-tenant trade reconciliation engine.

                                **Authentication:**
                                - Public endpoints (`/auth/**`, `/tenants/register`) require no credentials.
                                - All other endpoints require a **Bearer JWT** (from `/auth/login`) in the `Authorization` header.
                                - Tenant-scoped endpoints additionally require an **X-Api-Key** header (returned at tenant registration).

                                **Multi-tenancy:** Each API key maps to an isolated PostgreSQL schema. \
                                All reads/writes are automatically scoped to the caller's tenant.
                                """)
                        .version("1.0.0 (M1)")
                        .contact(new Contact().name("Consensus Platform")))
                .servers(List.of(
                        new Server().url("https://api.getconsensus.xyz").description("Production"),
                        new Server().url("http://localhost:8080").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .name("BearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT issued by POST /auth/login"))
                        .addSecuritySchemes("ApiKeyAuth",
                                new SecurityScheme()
                                        .name("X-Api-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Tenant API key returned at POST /tenants/register")));
    }
}
