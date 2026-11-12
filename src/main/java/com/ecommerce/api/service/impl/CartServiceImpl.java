package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.CartResolver;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service implementation for managing shopping carts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartResolver cartResolver;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CartResponse getOrCreateCart(User user, String cartToken) {
        Cart cart = resolveOrCreateCart(user, cartToken);
        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse addToCart(User user, String cartToken, AddToCartRequest request) {
        Objects.requireNonNull(request, "Add to cart request cannot be null.");

        Cart cart = resolveOrCreateCart(user, cartToken);
        Product product = productRepository.getWithImagesOrThrow(request.productId());

        CartItem existingItem = findCartItem(cart, product.getId());
        long existingQuantity = existingItem != null ? existingItem.getQuantity() : 0L;

        long targetQuantity = existingQuantity + request.quantity();
        validateStockAvailability(product, targetQuantity);

        cart.addProduct(product, request.quantity());
        log.info("Added product id={} quantity={} to cart id={}", product.getId(), request.quantity(), cart.getId());

        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(User user, String cartToken, Long productId, UpdateCartItemRequest request) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        Objects.requireNonNull(request, "Update cart item request cannot be null.");

        Cart cart = resolveOrCreateCart(user, cartToken);
        CartItem targetItem = findCartItemOrThrow(cart, productId);

        Product product = productRepository.getOrThrow(productId);
        validateStockAvailability(product, (long) request.quantity());

        targetItem.setQuantity(request.quantity());
        log.info("Updated product id={} quantity to {} in cart id={}", productId, request.quantity(), cart.getId());

        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(User user, String cartToken, Long productId) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");

        Cart cart = resolveOrCreateCart(user, cartToken);
        cart.removeProduct(productId);
        log.info("Removed product id={} from cart id={}", productId, cart.getId());

        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public void clearCart(User user, String cartToken) {
        Cart cart = resolveOrCreateCart(user, cartToken);
        cart.clear();
        log.info("Cleared cart id={}", cart.getId());
    }

    private Cart resolveOrCreateCart(User user, String cartToken) {
        if (user != null) {
            return cartResolver.findForUser(user, cartToken).orElseGet(() -> cartResolver.createCart(user));
        }
        return cartResolver.findGuest(cartToken).orElseGet(() -> cartResolver.createCart(null));
    }

    private void validateStockAvailability(Product product, long requestedQuantity) {
        if (requestedQuantity > product.getStockQuantity()) {
            log.warn("Insufficient stock for product id={}: requested={}, available={}", product.getId(), requestedQuantity, product.getStockQuantity());
            throw InsufficientStockException.of(product, requestedQuantity);
        }
    }

    private CartItem findCartItem(Cart cart, Long productId) {
        for (CartItem item : cart.getItems()) {
            if (item.getProduct() != null && productId.equals(item.getProduct().getId())) {
                return item;
            }
        }
        return null;
    }

    private CartItem findCartItemOrThrow(Cart cart, Long productId) {
        CartItem item = findCartItem(cart, productId);
        if (item == null) {
            throw new ResourceNotFoundException("Item with product id " + productId + " not found in cart.");
        }
        return item;
    }
}
