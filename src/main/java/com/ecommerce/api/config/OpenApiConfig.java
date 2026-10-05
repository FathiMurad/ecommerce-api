package com.ecommerce.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration bean initializing OpenAPI 3.0 specification metadata.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title("E-Commerce REST API").version("1.0.0").description("Production-grade e-commerce backend API covering Catalog, Shopping Cart, and Order Checkout modules.").contact(new Contact().name("Fathi Murad").email("fathi@example.com")).license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}
