package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.ProductRequest;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for product catalog operations,
 * supporting paginated browsing, category filtering, and administrative management.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Endpoints for catalog product browsing, searching, and administrative management")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Get all products (Paginated)", description = "Retrieves a paginated list of catalog products with sorting capabilities. Public access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved paginated products", content = @Content(schema = @Schema(implementation = PagedResponse.class)))})
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(@Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page, @Parameter(description = "Number of records per page (max 100)") @RequestParam(defaultValue = "10") int size, @Parameter(description = "Sorting property: id, name, price, stockQuantity") @RequestParam(defaultValue = "id") String sortBy, @Parameter(description = "Sorting direction: asc or desc") @RequestParam(defaultValue = "asc") String sortDirection) {
        PagedResponse<ProductResponse> products = productService.getAllProducts(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Get product by ID", description = "Retrieves a single product by its unique identifier including associated gallery images. Public access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Product found", content = @Content(schema = @Schema(implementation = ProductResponse.class))), @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)})
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@Parameter(description = "Product unique identifier", required = true) @PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Get products by Category ID (Paginated)", description = "Retrieves a paginated list of products belonging to a specific category. Public access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved products for category", content = @Content(schema = @Schema(implementation = PagedResponse.class))), @ApiResponse(responseCode = "404", description = "Category not found", content = @Content)})
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByCategoryId(@Parameter(description = "Category unique identifier", required = true) @PathVariable Long categoryId, @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page, @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "10") int size, @Parameter(description = "Sorting property") @RequestParam(defaultValue = "id") String sortBy, @Parameter(description = "Sorting direction") @RequestParam(defaultValue = "asc") String sortDirection) {
        PagedResponse<ProductResponse> products = productService.getProductsByCategoryId(categoryId, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Create a new catalog product", description = "Creates a product with optional gallery image URLs. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Product created successfully", content = @Content(schema = @Schema(implementation = ProductResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid product payload", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Assigned category does not exist", content = @Content)})
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse createdProduct = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @Operation(summary = "Update an existing product", description = "Updates product details and synchronizes gallery images. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Product updated successfully", content = @Content(schema = @Schema(implementation = ProductResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid product payload", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Product or category not found", content = @Content)})
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(@Parameter(description = "Product unique identifier", required = true) @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        ProductResponse updatedProduct = productService.updateProduct(id, request);
        return ResponseEntity.ok(updatedProduct);
    }

    @Operation(summary = "Delete a product", description = "Removes a product and its image associations from the catalog. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Product deleted successfully"), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)})
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@Parameter(description = "Product unique identifier", required = true) @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
