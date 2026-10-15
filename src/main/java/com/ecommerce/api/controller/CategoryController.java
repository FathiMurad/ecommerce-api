package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.CategoryRequest;
import com.ecommerce.api.dto.response.CategoryResponse;
import com.ecommerce.api.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints for category catalog operations,
 * supporting public catalog browsing and administrative category management.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Endpoints for catalog category navigation and administrative management")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Get all categories", description = "Retrieves all available catalog departments/categories. Public access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved categories", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))))})
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    @Operation(summary = "Get category by ID", description = "Retrieves a single category by its unique identifier. Public access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Category found", content = @Content(schema = @Schema(implementation = CategoryResponse.class))), @ApiResponse(responseCode = "404", description = "Category not found", content = @Content)})
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@Parameter(description = "Category unique identifier", required = true) @PathVariable Long id) {
        CategoryResponse category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    @Operation(summary = "Create a new category", description = "Creates a new catalog category. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Category created successfully", content = @Content(schema = @Schema(implementation = CategoryResponse.class))), @ApiResponse(responseCode = "400", description = "Category name already exists or payload is invalid", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content)})
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse createdCategory = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    @Operation(summary = "Update an existing category", description = "Updates the name and description of a category. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Category updated successfully", content = @Content(schema = @Schema(implementation = CategoryResponse.class))), @ApiResponse(responseCode = "400", description = "Target category name already in use by another category", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Category not found", content = @Content)})
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(@Parameter(description = "Category unique identifier", required = true) @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse updatedCategory = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(updatedCategory);
    }

    @Operation(summary = "Delete a category", description = "Deletes an empty category. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Category deleted successfully"), @ApiResponse(responseCode = "400", description = "Cannot delete category with associated products", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Category not found", content = @Content)})
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@Parameter(description = "Category unique identifier", required = true) @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
