package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * REST controller exposing endpoints for shopping cart operations.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader(value = "X-Cart-Token", required = false) String cartToken) {
        CartResponse cart = cartService.getOrCreateCart(cartToken);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(@RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @RequestBody AddToCartRequest request) {
        CartResponse cart = cartService.addToCart(cartToken, request);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(@RequestHeader(value = "X-Cart-Token") String cartToken, @PathVariable Long productId, @RequestBody UpdateCartItemRequest request) {
        CartResponse cart = cartService.updateCartItem(cartToken, productId, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItemFromCart(@RequestHeader(value = "X-Cart-Token") String cartToken, @PathVariable Long productId) {
        CartResponse cart = cartService.removeCartItem(cartToken, productId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestHeader(value = "X-Cart-Token") String cartToken) {
        cartService.clearCart(cartToken);
        return ResponseEntity.noContent().build();
    }
}
