package com.ecommerce.api.bootstrap;

import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.ProductImage;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds initial catalog data and essential administrative accounts
 * upon application bootstrap if they do not exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedAdminUser();
        seedCatalog();
    }

    /**
     * Ensures an administrative account exists for platform operations.
     */
    private void seedAdminUser() {
        String adminEmail = "admin@ecommerce.com";

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = User.builder().firstName("Super").lastName("Admin").email(adminEmail).password(passwordEncoder.encode("Admin@123456")).role(Role.ROLE_ADMIN).build();

            userRepository.save(admin);
            log.info("Initialized default ADMIN account: {}", adminEmail);
        }
    }

    /**
     * Seeds initial catalog categories, products, and images if catalog is empty.
     */
    private void seedCatalog() {
        if (categoryRepository.count() > 0) {
            log.info("Database catalog already seeded. Skipping catalog seeding.");
            return;
        }

        log.info("Starting database catalog seeding...");

        Category electronics = Category.builder().name("Electronics").description("Consumer tech, laptops, and smart gadgets").build();

        Category accessories = Category.builder().name("Accessories").description("Cables, chargers, protective sleeves, and desk gear").build();

        categoryRepository.saveAll(List.of(electronics, accessories));

        Product macbook = Product.builder().name("Pro Laptop 16\" M3 Max").description("High-performance laptop engineered for software developers and creative workflows.").price(new BigDecimal("2499.00")).stockQuantity(15).category(electronics).build();

        macbook.addImage(ProductImage.builder().imageUrl("https://images.unsplash.com/photo-1517336714731-489689fd1ca8").isPrimary(true).displayOrder(0).build());

        macbook.addImage(ProductImage.builder().imageUrl("https://images.unsplash.com/photo-1611186871348-b1ce696e52c9").isPrimary(false).displayOrder(1).build());

        Product keyboard = Product.builder().name("Wireless Mechanical Keyboard").description("Compact 75% hot-swappable keyboard with tactile switches and RGB backlighting.").price(new BigDecimal("129.50")).stockQuantity(40).category(accessories).build();

        keyboard.addImage(ProductImage.builder().imageUrl("https://images.unsplash.com/photo-1587829741301-dc798b83add3").isPrimary(true).displayOrder(0).build());

        productRepository.saveAll(List.of(macbook, keyboard));
        log.info("Database seeding completed successfully. Added 2 categories and 2 products with images.");
    }
}
