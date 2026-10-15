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
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CartService providing transactional shopping cart operations
 * supporting authenticated user carts, guest session fallback, and atomic cart merging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    public CartResponse getOrCreateCart(User user, String cartToken) {
        Cart cart = resolveCart(user, cartToken);
        return CartResponse.fromEntity(cart);
    }

    @Override
    public CartResponse addToCart(User user, String cartToken, AddToCartRequest request) {
        if (request.productId() == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Cart cart = resolveCart(user, cartToken);

        Product product = productRepository.findByIdWithImages(request.productId()).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));

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
    public CartResponse updateCartItem(User user, String cartToken, Long productId, UpdateCartItemRequest request) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            return removeCartItem(user, cartToken, productId);
        }

        Cart cart = resolveCart(user, cartToken);

        CartItem targetItem = cart.getItems().stream().filter(item -> item.getProduct().getId().equals(productId)).findFirst().orElseThrow(() -> new ResourceNotFoundException("Item with product id " + productId + " not found in cart."));

        // Fetch fresh product state to validate against actual current inventory
        Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (request.quantity() > product.getStockQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Requested: " + request.quantity() + ", Available: " + product.getStockQuantity());
        }

        targetItem.setQuantity(request.quantity());
        Cart savedCart = cartRepository.save(cart);

        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public CartResponse removeCartItem(User user, String cartToken, Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        Cart cart = resolveCart(user, cartToken);
        cart.removeProduct(productId);
        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public void clearCart(User user, String cartToken) {
        Cart cart = resolveCart(user, cartToken);
        cart.clear();
        cartRepository.save(cart);
    }

    /**
     * Resolves the cart prioritizing the authenticated user.
     * Merges guest cart items into the user's existing cart, claims an anonymous cart
     * if the user does not have one, or creates a fresh session cart.
     */
    private Cart resolveCart(User user, String cartToken) {
        if (user != null) {
            Optional<Cart> userCartOpt = cartRepository.findByUserIdWithItems(user.getId());
            boolean hasGuestToken = cartToken != null && !cartToken.trim().isEmpty();

            if (userCartOpt.isPresent()) {
                Cart userCart = userCartOpt.get();
                if (hasGuestToken) {
                    Optional<Cart> guestCartOpt = cartRepository.findByCartTokenWithItems(cartToken.trim());
                    if (guestCartOpt.isPresent()) {
                        Cart guestCart = guestCartOpt.get();
                        // Merge only if guest cart is anonymous and separate from the user cart
                        if (guestCart.getUser() == null && !guestCart.getId().equals(userCart.getId())) {
                            log.info("Merging guest cart (token={}) into user cart (id={}) for user id={}", guestCart.getCartToken(), userCart.getId(), user.getId());
                            mergeGuestCartIntoUserCart(userCart, guestCart);
                            return cartRepository.save(userCart);
                        }
                    }
                }
                return userCart;
            }

            // User has no cart yet. Claim the guest cart if valid and anonymous
            if (hasGuestToken) {
                Optional<Cart> guestCartOpt = cartRepository.findByCartTokenWithItems(cartToken.trim());
                if (guestCartOpt.isPresent()) {
                    Cart guestCart = guestCartOpt.get();
                    if (guestCart.getUser() == null) {
                        log.info("Claiming anonymous guest cart (token={}) for user id={}", guestCart.getCartToken(), user.getId());
                        guestCart.setUser(user);
                        return cartRepository.save(guestCart);
                    }
                }
            }

            // Create a brand new cart assigned directly to the authenticated user
            Cart newCart = Cart.builder().cartToken(UUID.randomUUID().toString()).user(user).items(new ArrayList<>()).build();

            return cartRepository.save(newCart);
        }

        // Guest session fallback
        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Optional<Cart> existingCart = cartRepository.findByCartTokenWithItems(cartToken.trim());
            if (existingCart.isPresent()) {
                Cart cart = existingCart.get();
                // Security check: Never allow an unauthenticated guest to hijack a registered user's cart
                if (cart.getUser() == null) {
                    return cart;
                }
                log.warn("Guest attempted to access authenticated user cart via token. Generating a new guest cart.");
            }
        }

        Cart newGuestCart = Cart.builder().cartToken(UUID.randomUUID().toString()).items(new ArrayList<>()).build();

        return cartRepository.save(newGuestCart);
    }

    /**
     * Merges all items from an anonymous guest cart into an authenticated user's cart,
     * respecting available inventory, and deletes the guest cart.
     */
    private void mergeGuestCartIntoUserCart(Cart userCart, Cart guestCart) {
        List<CartItem> guestItems = new ArrayList<>(guestCart.getItems());
        for (CartItem guestItem : guestItems) {
            Product product = guestItem.getProduct();
            int guestQty = guestItem.getQuantity();

            Optional<CartItem> existingItemOpt = userCart.getItems().stream().filter(item -> item.getProduct().getId().equals(product.getId())).findFirst();

            if (existingItemOpt.isPresent()) {
                CartItem existingItem = existingItemOpt.get();
                int totalQty = existingItem.getQuantity() + guestQty;
                int finalQty = Math.min(totalQty, product.getStockQuantity());
                existingItem.setQuantity(finalQty);
            } else {
                int finalQty = Math.min(guestQty, product.getStockQuantity());
                if (finalQty > 0) {
                    userCart.addProduct(product, finalQty);
                }
            }
        }

        cartRepository.delete(guestCart);
    }
}
