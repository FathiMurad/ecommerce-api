package com.ecommerce.api;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Entry point for the E-Commerce RESTful API service, bootstrapping Spring context and enforcing UTC timezone.
 */
@SpringBootApplication
public class EcommerceApiApplication {

    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApiApplication.class, args);
    }
}
