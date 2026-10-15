package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.CartService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for shopping cart operations,
 * supporting both authenticated users via JWT and anonymous guest sessions via X-Cart-Token header.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Shopping Cart", description = "Endpoints for managing user and guest shopping carts")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get or create active shopping cart", description = "Retrieves the active cart for the authenticated user or anonymous session token. Provisions a new cart if none exists.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved active shopping cart", content = @Content(schema = @Schema(implementation = CartResponse.class)))})
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (UUID)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.getOrCreateCart(user, cartToken);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Add an item to the shopping cart", description = "Appends a product or increments its quantity in the shopping cart.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Item added to cart successfully", content = @Content(schema = @Schema(implementation = CartResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid payload or insufficient stock", content = @Content), @ApiResponse(responseCode = "404", description = "Product or cart not found", content = @Content)})
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (UUID)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @Valid @RequestBody AddToCartRequest request) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.addToCart(user, cartToken, request);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Update item quantity in cart", description = "Sets an exact quantity for a specific product inside the cart.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Cart item quantity updated successfully", content = @Content(schema = @Schema(implementation = CartResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid quantity or insufficient stock", content = @Content), @ApiResponse(responseCode = "404", description = "Product not found in cart", content = @Content)})
    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (UUID)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @Parameter(description = "Product ID to update", required = true) @PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.updateCartItem(user, cartToken, productId, request);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Remove an item from cart", description = "Completely removes a line item from the shopping cart by product ID.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Item removed successfully", content = @Content(schema = @Schema(implementation = CartResponse.class))), @ApiResponse(responseCode = "404", description = "Product or cart not found", content = @Content)})
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItemFromCart(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (UUID)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @Parameter(description = "Product ID to remove", required = true) @PathVariable Long productId) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.removeCartItem(user, cartToken, productId);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Clear shopping cart", description = "Removes all items from the active shopping cart.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Cart cleared successfully"), @ApiResponse(responseCode = "404", description = "Cart not found", content = @Content)})
    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (UUID)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken) {
        User user = extractUser(securityUser);
        cartService.clearCart(user, cartToken);
        return ResponseEntity.noContent().build();
    }

    private User extractUser(SecurityUser securityUser) {
        return securityUser != null ? securityUser.getUser() : null;
    }
}
