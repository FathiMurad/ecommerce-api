package com.ecommerce.api.bootstrap;

import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.ProductImage;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise database seeder executing upon application startup.
 * Idempotently initializes and synchronizes System Administrator & Default Customer credentials,
 * provisions default shopping carts, and populates the supermarket catalog.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("SpellCheckingInspection")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin-email:${app.init.admin.email:admin@ecommerce.com}}")
    private String adminEmail;

    @Value("${app.init.admin-password:${app.init.admin.password:Admin123456!}}")
    private String adminPassword;

    @Value("${app.init.customer-email:${app.init.customer.email:customer@ecommerce.com}}")
    private String customerEmail;

    @Value("${app.init.customer-password:${app.init.customer.password:Customer123456!}}")
    private String customerPassword;

    private static final String IMAGE_BASE_PATH = "/images/products/";

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        seedUsersAndCarts();
        seedSupermarketCatalog();
    }

    /**
     * Idempotently provisions or updates credentials for System Administrator and Default Customer.
     * Evaluates existing password matches to prevent costly BCrypt re-hashing on every boot.
     */
    private void seedUsersAndCarts() {
        String normalizedAdminEmail = adminEmail.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalizedAdminEmail)) {
            User admin = User.builder().firstName("Super").lastName("Admin").email(normalizedAdminEmail).password(passwordEncoder.encode(adminPassword)).role(Role.ROLE_ADMIN).isActive(true).build();
            userRepository.save(admin);
            log.info("Default Admin initialized with email={}", normalizedAdminEmail);
        } else {
            log.info("Admin user already exists. Preserving existing admin credentials without overwriting.");
        }

        String normalizedCustomerEmail = customerEmail.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalizedCustomerEmail)) {
            User customer = User.builder().firstName("Default").lastName("Customer").email(normalizedCustomerEmail).password(passwordEncoder.encode(customerPassword)).role(Role.ROLE_CUSTOMER).isActive(true).build();
            User savedCustomer = userRepository.save(customer);

            Cart customerCart = Cart.builder().user(savedCustomer).cartToken(UUID.randomUUID().toString()).build();
            cartRepository.save(customerCart);
            log.info("Default Customer and initial cart initialized with email={}", normalizedCustomerEmail);
        } else {
            log.info("Default Customer already exists. Preserving existing customer credentials.");
        }
    }

    /**
     * Seeds the complete supermarket catalog across dynamic departments if empty.
     */
    private void seedSupermarketCatalog() {
        if (categoryRepository.count() > 0) {
            log.info("Catalog categories already exist ({} found). Skipping catalog seeding.", categoryRepository.count());
            return;
        }

        log.info("Starting comprehensive supermarket catalog seeding...");

        Category dairy = Category.builder().name("Dairy & Eggs").description("Fresh milk, cheeses, butter, and farm eggs").build();
        Category produce = Category.builder().name("Fruits & Vegetables").description("Farm-fresh organic vegetables and seasonal fruits").build();
        Category meat = Category.builder().name("Meat & Poultry").description("Premium cuts of beef, fresh chicken, and lamb").build();
        Category bakery = Category.builder().name("Bakery").description("Freshly baked artisan breads, pastries, and buns").build();
        Category beverages = Category.builder().name("Beverages").description("Mineral waters, natural juices, coffee, and sodas").build();
        Category snacks = Category.builder().name("Snacks & Sweets").description("Chips, premium chocolates, biscuits, and roasted nuts").build();
        Category pantry = Category.builder().name("Pantry Staples").description("Pasta, grains, extra virgin oils, sauces, and spices").build();

        List<Category> categories = List.of(dairy, produce, meat, bakery, beverages, snacks, pantry);
        categoryRepository.saveAll(categories);

        List<Product> catalog = new ArrayList<>();

        // 1. Dairy & Eggs
        catalog.add(createProduct("Fresh Milk 3%", "Locally sourced fresh cow's milk with 3% fat content, 1L carton.", "6.50", 120, dairy, "milk.png"));
        catalog.add(createProduct("Cheddar Cheese", "Aged cheddar cheese block with a sharp, rich flavor, 200g.", "18.90", 45, dairy, "cheddar.png"));
        catalog.add(createProduct("Organic Eggs", "Pack of 12 free-range organic eggs from local farms.", "24.00", 60, dairy, "eggs.png"));
        catalog.add(createProduct("Greek Yogurt", "Thick and creamy natural Greek yogurt, high in protein, 500g.", "11.20", 80, dairy, "yogurt.png"));
        catalog.add(createProduct("Unsalted Butter", "Sweet cream pure unsalted butter, ideal for baking, 200g.", "9.50", 95, dairy, "butter.png"));
        catalog.add(createProduct("Cottage Cheese 5%", "Classic creamy cottage cheese with 5% fat, 250g tub.", "5.90", 110, dairy, "cottage-cheese.png"));
        catalog.add(createProduct("Cream Cheese", "Rich and spreadable white cream cheese, 200g tub.", "10.50", 70, dairy, "cream-cheese.png"));
        catalog.add(createProduct("Chocolate Milk", "Sweet chocolate-flavored dairy drink, ready to serve, 1L bottle.", "8.20", 85, dairy, "chocolate-milk.png"));
        catalog.add(createProduct("Gouda Slices", "Premium Dutch Gouda cheese, sliced and ready to serve, 150g.", "16.90", 40, dairy, "gouda.png"));
        catalog.add(createProduct("Sour Cream 15%", "Traditional sour cream, perfect for cooking and toppings, 200g.", "4.20", 90, dairy, "sour-cream.png"));

        // 2. Fruits & Vegetables
        catalog.add(createProduct("Green Apples", "Crisp and tart Granny Smith apples, 1 kg.", "12.90", 200, produce, "green-apples.png"));
        catalog.add(createProduct("Cherry Tomatoes", "Sweet organic cherry tomatoes, perfect for salads, 500g box.", "14.00", 150, produce, "cherry-tomatoes.png"));
        catalog.add(createProduct("Fresh Bananas", "Sweet ripe yellow bananas, imported, 1 kg.", "8.50", 250, produce, "bananas.png"));
        catalog.add(createProduct("Hass Avocado", "Ready-to-eat creamy Hass avocados, 1 kg.", "19.90", 65, produce, "avocado.png"));
        catalog.add(createProduct("Red Potatoes", "Premium baking red potatoes, ideal for roasting, 2.5 kg bag.", "13.50", 120, produce, "red-potatoes.png"));
        catalog.add(createProduct("Red Onions", "Fresh sharp red onions, 1 kg.", "7.90", 180, produce, "red-onions.png"));
        catalog.add(createProduct("Local Cucumbers", "Crisp green field cucumbers, freshly harvested, 1 kg.", "6.50", 220, produce, "cucumbers.png"));
        catalog.add(createProduct("Organic Carrots", "Sweet organic orange carrots, washed and ready to eat, 1 kg bag.", "5.80", 140, produce, "carrots.png"));
        catalog.add(createProduct("Iceberg Lettuce", "Fresh crunchy iceberg lettuce head.", "6.90", 90, produce, "lettuce.png"));
        catalog.add(createProduct("Fresh Strawberries", "Sweet seasonal red strawberries, 500g pack.", "18.00", 50, produce, "strawberries.png"));
        catalog.add(createProduct("Watermelon", "Sweet seedless whole watermelon, per unit.", "29.90", 40, produce, "watermelon.png"));
        catalog.add(createProduct("Fresh Lemons", "Juicy yellow sour lemons, great for cooking and drinks, 1 kg.", "9.20", 130, produce, "lemons.png"));
        catalog.add(createProduct("Garlic Pearls", "Mesh pack of white garlic, 3 whole heads.", "5.00", 160, produce, "garlic.png"));

        // 3. Meat & Poultry
        catalog.add(createProduct("Chicken Breast", "Fresh boneless, skinless chicken breast fillets, 1 kg.", "35.00", 40, meat, "chicken-breast.png"));
        catalog.add(createProduct("Minced Beef", "Premium fresh lean ground beef, 500g.", "42.00", 35, meat, "minced-beef.png"));
        catalog.add(createProduct("Ribeye Steak", "Fresh premium aged ribeye beef steak with rich marbling, 1 kg.", "125.00", 15, meat, "ribeye-steak.png"));
        catalog.add(createProduct("Chicken Wings", "Fresh chicken wings, great for grilling, 1 kg pack.", "19.90", 50, meat, "chicken-wings.png"));
        catalog.add(createProduct("Beef Burger Patties", "Pack of 4 gourmet seasoned beef burger patties.", "28.50", 35, meat, "beef-patties.png"));
        catalog.add(createProduct("Fresh Turkey Breast", "Sliced deli-ready fresh turkey breast, 1 kg.", "48.00", 20, meat, "turkey-breast.png"));
        catalog.add(createProduct("Premium Lamb Chops", "Fresh local lamb chops, tender cut, 1 kg.", "140.00", 12, meat, "lamb-chops.png"));

        // 4. Bakery
        catalog.add(createProduct("Whole Wheat Bread", "Freshly baked high-fiber sliced wheat bread.", "13.90", 65, bakery, "wheat-bread.png"));
        catalog.add(createProduct("Butter Croissant", "Flaky French-style butter croissant, baked fresh daily.", "4.50", 120, bakery, "croissant.png"));
        catalog.add(createProduct("Chocolate Muffin", "Rich double chocolate chunk bakery muffin.", "6.90", 55, bakery, "chocolate-muffin.png"));
        catalog.add(createProduct("French Baguette", "Traditional crusty long white baguette.", "7.00", 80, bakery, "baguette.png"));
        catalog.add(createProduct("White Pita Bread", "Pack of 5 freshly baked soft white pitas.", "5.50", 150, bakery, "pita-bread.png"));
        catalog.add(createProduct("Classic White Loaf", "Soft sliced white sandwich bread loaf.", "9.90", 75, bakery, "white-loaf.png"));
        catalog.add(createProduct("Hamburger Buns", "Pack of 6 soft white sesame hamburger buns.", "11.00", 100, bakery, "hamburger-buns.png"));
        catalog.add(createProduct("Apple Pie Tart", "Sweet baked apple cinnamon tart with a golden lattice crust.", "26.00", 30, bakery, "apple-tart.png"));

        // 5. Beverages
        catalog.add(createProduct("Mineral Water Pack", "Pack of six bottles of pure natural mineral water, 1.5L each.", "11.50", 300, beverages, "mineral-water.png"));
        catalog.add(createProduct("Pure Orange Juice", "100% natural pasteurized orange juice, no added sugar, 1L.", "14.90", 95, beverages, "orange-juice.png"));
        catalog.add(createProduct("Classic Cola Can", "Carbonated fizzy soft drink can, 330ml.", "5.00", 500, beverages, "cola-can.png"));
        catalog.add(createProduct("Natural Apple Juice", "Clear sweet apple juice, no preservatives, 1L bottle.", "12.00", 80, beverages, "apple-juice.png"));
        catalog.add(createProduct("Soda Water Pack", "Pack of 6 refreshing sparkling soda water cans.", "13.50", 140, beverages, "soda-water.png"));
        catalog.add(createProduct("Cold Brew Coffee", "Ready-to-drink unsweetened iced black coffee.", "9.90", 75, beverages, "cold-brew.png"));
        catalog.add(createProduct("Energy Drink", "High-caffeine functional energy beverage can.", "8.50", 260, beverages, "energy-drink.png"));

        // 6. Snacks & Sweets
        catalog.add(createProduct("Salty Potato Chips", "Classic salted crispy potato chips, 200g bag.", "7.90", 210, snacks, "potato-chips.png"));
        catalog.add(createProduct("Dark Chocolate 70%", "Premium cocoa Swiss dark chocolate bar, 70% cocoa, 100g.", "11.90", 160, snacks, "dark-chocolate.png"));
        catalog.add(createProduct("Tortilla Corn Chips", "Crunchy salted nacho-flavored corn chips, large pack.", "9.50", 140, snacks, "tortilla-chips.png"));
        catalog.add(createProduct("Fruity Gummy Bears", "Chewy sweet assorted fruit-flavored gummy candies, 150g.", "6.00", 200, snacks, "gummy-bears.png"));
        catalog.add(createProduct("Roasted Peanuts", "Salted golden roasted peanuts in a resealable tin, 250g.", "10.50", 115, snacks, "roasted-peanuts.png"));
        catalog.add(createProduct("Chocolate Cookies", "Crunchy cookies baked with real chocolate chips.", "12.50", 130, snacks, "chocolate-cookies.png"));
        catalog.add(createProduct("Microwave Popcorn", "Pack of 3 butter-flavored microwave popcorn bags.", "13.90", 95, snacks, "popcorn.png"));
        catalog.add(createProduct("Hazelnut Wafers", "Crispy wafer layers filled with rich hazelnut cream.", "8.00", 175, snacks, "hazelnut-wafers.png"));

        // 7. Pantry Staples
        catalog.add(createProduct("White Rice 1kg", "Long-grain premium polished white rice.", "8.90", 250, pantry, "rice.png"));
        catalog.add(createProduct("Spaghetti Pasta", "Traditional Italian durum wheat semolina pasta, 500g.", "5.50", 320, pantry, "spaghetti.png"));
        catalog.add(createProduct("Extra Virgin Olive Oil", "Cold-pressed premium extra virgin olive oil, 1L bottle.", "34.90", 85, pantry, "olive-oil.png"));
        catalog.add(createProduct("Tomato Paste Sauce", "Rich Italian crushed tomato sauce, 400g can.", "4.50", 400, pantry, "tomato-paste.png"));
        catalog.add(createProduct("White Flour 1kg", "All-purpose refined white baking flour.", "6.00", 190, pantry, "flour.png"));
        catalog.add(createProduct("White Sugar 1kg", "Pure granulated white cane sugar bag.", "5.20", 230, pantry, "sugar.png"));
        catalog.add(createProduct("Fine Sea Salt", "Iodized fine cooking table salt shaker, 1kg.", "3.50", 300, pantry, "sea-salt.png"));
        catalog.add(createProduct("Canned Tuna Pack", "Pack of 4 tuna flakes preserved in vegetable oil.", "22.90", 150, pantry, "canned-tuna.png"));
        catalog.add(createProduct("Tomato Ketchup", "Classic sweet tomato condiment, squeeze bottle, 500g.", "10.00", 180, pantry, "ketchup.png"));
        catalog.add(createProduct("Real Mayonnaise", "Rich and creamy traditional egg mayonnaise, jar, 400g.", "13.50", 125, pantry, "mayonnaise.png"));

        productRepository.saveAll(catalog);
        log.info("Catalog seeding complete: Saved {} products across {} departments.", catalog.size(), categories.size());
    }

    /**
     * Factory helper to construct a Product with an associated primary ProductImage.
     */
    private Product createProduct(String name, String desc, String price, int stock, Category category, String imgFileName) {
        Product product = Product.builder().name(name).description(desc).price(new BigDecimal(price)).stockQuantity(stock).category(category).build();

        ProductImage image = ProductImage.builder().imageUrl(IMAGE_BASE_PATH + imgFileName).isPrimary(true).displayOrder(0).build();

        product.addImage(image);
        return product;
    }
}
