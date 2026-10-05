package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CartService providing transactional shopping cart operations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    public CartResponse getOrCreateCart(String cartToken) {
        Cart cart = resolveCart(cartToken);
        return CartResponse.fromEntity(cart);
    }

    @Override
    public CartResponse addToCart(String cartToken, AddToCartRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Cart cart = resolveCart(cartToken);

        Optional<Product> productOptional = productRepository.findByIdWithImages(request.productId());
        if (productOptional.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + request.productId());
        }

        Product product = productOptional.get();

        int existingQuantity = 0;
        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getId().equals(product.getId())) {
                existingQuantity = item.getQuantity();
                break;
            }
        }

        int targetQuantity = existingQuantity + request.quantity();
        if (targetQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Requested: " + targetQuantity + ", Available: " + product.getStockQuantity());
        }

        cart.addProduct(product, request.quantity());
        Cart savedCart = cartRepository.save(cart);

        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public CartResponse updateCartItem(String cartToken, Long productId, UpdateCartItemRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            return removeCartItem(cartToken, productId);
        }

        Cart cart = resolveCart(cartToken);

        CartItem targetItem = null;
        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getId().equals(productId)) {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null) {
            throw new ResourceNotFoundException("Item with product id " + productId + " not found in cart.");
        }

        if (request.quantity() > targetItem.getProduct().getStockQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product '" + targetItem.getProduct().getName() + "'. Requested: " + request.quantity() + ", Available: " + targetItem.getProduct().getStockQuantity());
        }

        targetItem.setQuantity(request.quantity());
        Cart savedCart = cartRepository.save(cart);

        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public CartResponse removeCartItem(String cartToken, Long productId) {
        Cart cart = resolveCart(cartToken);
        cart.removeProduct(productId);
        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public void clearCart(String cartToken) {
        Cart cart = resolveCart(cartToken);
        cart.clear();
        cartRepository.save(cart);
    }

    /**
     * Resolves an existing cart by token or generates a new one if not found.
     */
    private Cart resolveCart(String cartToken) {
        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Optional<Cart> existingCart = cartRepository.findByCartTokenWithItems(cartToken);
            if (existingCart.isPresent()) {
                return existingCart.get();
            }
        }

        Cart newCart = Cart.builder().cartToken(UUID.randomUUID().toString()).items(new ArrayList<>()).build();

        return cartRepository.save(newCart);
    }
}
