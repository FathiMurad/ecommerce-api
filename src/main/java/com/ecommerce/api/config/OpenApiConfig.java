package com.ecommerce.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures OpenAPI metadata and JWT security requirements for protected and guest-accessible endpoints.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce REST API")
                        .version("1.0.0")
                        .description("Production-minded e-commerce backend API covering Catalog, Shopping Cart, Order Checkout, and Simulated Payment modules.")
                        .contact(new Contact().name("Fathi Murad").email("fathi@example.com"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer optionalAuthCustomizer() {
        // Endpoints supporting both guest tokens and authenticated JWTs require an empty fallback SecurityRequirement
        List<String> optionalAuthPaths = List.of(
                "/api/cart",
                "/api/cart/items",
                "/api/cart/items/{productId}",
                "/api/orders/checkout",
                "/api/orders/{trackingNumber}",
                "/api/payments/orders/{trackingNumber}"
        );

        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (optionalAuthPaths.contains(path)) {
                    pathItem.readOperations().forEach(operation -> operation.setSecurity(
                            List.of(new SecurityRequirement().addList(SECURITY_SCHEME_NAME), new SecurityRequirement())
                    ));
                }
            });
        };
    }
}
