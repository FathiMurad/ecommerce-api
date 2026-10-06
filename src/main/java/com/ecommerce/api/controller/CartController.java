package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * supporting both authenticated users via JWT and anonymous sessions.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.getOrCreateCart(user, cartToken);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @RequestBody AddToCartRequest request) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.addToCart(user, cartToken, request);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @PathVariable Long productId, @RequestBody UpdateCartItemRequest request) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.updateCartItem(user, cartToken, productId, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItemFromCart(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @PathVariable Long productId) {
        User user = extractUser(securityUser);
        CartResponse cart = cartService.removeCartItem(user, cartToken, productId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken) {
        User user = extractUser(securityUser);
        cartService.clearCart(user, cartToken);
        return ResponseEntity.noContent().build();
    }

    private User extractUser(SecurityUser securityUser) {
        return securityUser != null ? securityUser.getUser() : null;
    }
}
