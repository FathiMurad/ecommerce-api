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
 * Service implementation for managing shopping carts.
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
        validateAddToCartRequest(request);

        Cart cart = resolveCart(user, cartToken);
        Product product = findProductWithImagesOrThrow(request.productId());

        CartItem existingItem = findCartItem(cart, product.getId());
        int existingQuantity = existingItem != null ? existingItem.getQuantity() : 0;

        int targetQuantity = existingQuantity + request.quantity();
        validateStockAvailability(product, targetQuantity);

        cart.addProduct(product, request.quantity());
        Cart savedCart = cartRepository.save(cart);
        log.info("Added product id={} quantity={} to cart id={}", product.getId(), request.quantity(), savedCart.getId());

        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public CartResponse updateCartItem(User user, String cartToken, Long productId, UpdateCartItemRequest request) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        validateUpdateCartItemRequest(request);

        // Setting quantity to zero or negative explicitly removes the item from the cart
        if (request.quantity() <= 0) {
            return removeCartItem(user, cartToken, productId);
        }

        Cart cart = resolveCart(user, cartToken);
        CartItem targetItem = findCartItemOrThrow(cart, productId);

        Product product = findProductOrThrow(productId);
        validateStockAvailability(product, request.quantity());

        targetItem.setQuantity(request.quantity());
        Cart savedCart = cartRepository.save(cart);
        log.info("Updated product id={} quantity to {} in cart id={}", productId, request.quantity(), savedCart.getId());

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
        log.info("Removed product id={} from cart id={}", productId, savedCart.getId());

        return CartResponse.fromEntity(savedCart);
    }

    @Override
    public void clearCart(User user, String cartToken) {
        Cart cart = resolveCart(user, cartToken);
        cart.clear();
        cartRepository.save(cart);
        log.info("Cleared cart id={}", cart.getId());
    }

    private Cart resolveCart(User user, String cartToken) {
        if (user != null) {
            return resolveUserCart(user, cartToken);
        }

        return resolveGuestCart(cartToken);
    }

    private Cart resolveUserCart(User user, String cartToken) {
        Optional<Cart> userCartOpt = cartRepository.findByUserIdWithItems(user.getId());
        boolean hasGuestToken = cartToken != null && !cartToken.trim().isEmpty();

        if (userCartOpt.isPresent()) {
            Cart userCart = userCartOpt.get();
            if (hasGuestToken) {
                mergeGuestCartIfExists(userCart, cartToken.trim());
            }

            return userCart;
        }

        // Claim existing guest session items instead of creating a brand-new empty cart
        if (hasGuestToken) {
            Cart claimedCart = claimGuestCartIfExists(user, cartToken.trim());
            if (claimedCart != null) {
                return claimedCart;
            }
        }

        return createNewUserCart(user);
    }

    private Cart resolveGuestCart(String cartToken) {
        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Optional<Cart> existingCart = cartRepository.findByCartTokenWithItems(cartToken.trim());
            if (existingCart.isPresent()) {
                Cart cart = existingCart.get();
                // Security guard: Prevent unauthenticated guests from hijacking registered user carts
                if (cart.getUser() == null) {
                    return cart;
                }
                log.warn("Guest attempted to access authenticated user cart via token. Generating a new guest cart.");
            }
        }

        return createNewGuestCart();
    }

    private void mergeGuestCartIfExists(Cart userCart, String guestCartToken) {
        Optional<Cart> guestCartOpt = cartRepository.findByCartTokenWithItems(guestCartToken);
        if (guestCartOpt.isPresent()) {
            Cart guestCart = guestCartOpt.get();
            if (guestCart.getUser() == null && !guestCart.getId().equals(userCart.getId())) {
                log.info("Merging guest cart (token={}) into user cart (id={}) for user id={}", guestCart.getCartToken(), userCart.getId(), userCart.getUser().getId());
                mergeGuestCartIntoUserCart(userCart, guestCart);
                cartRepository.save(userCart);
            }
        }
    }

    private Cart claimGuestCartIfExists(User user, String guestCartToken) {
        Optional<Cart> guestCartOpt = cartRepository.findByCartTokenWithItems(guestCartToken);
        if (guestCartOpt.isPresent()) {
            Cart guestCart = guestCartOpt.get();
            if (guestCart.getUser() == null) {
                log.info("Claiming anonymous guest cart (token={}) for user id={}", guestCart.getCartToken(), user.getId());
                guestCart.setUser(user);

                return cartRepository.save(guestCart);
            }
        }

        return null;
    }

    private Cart createNewUserCart(User user) {
        Cart newCart = Cart.builder().cartToken(UUID.randomUUID().toString()).user(user).items(new ArrayList<>()).build();

        return cartRepository.save(newCart);
    }

    private Cart createNewGuestCart() {
        Cart newGuestCart = Cart.builder().cartToken(UUID.randomUUID().toString()).items(new ArrayList<>()).build();

        return cartRepository.save(newGuestCart);
    }

    private void mergeGuestCartIntoUserCart(Cart userCart, Cart guestCart) {
        List<CartItem> guestItems = new ArrayList<>(guestCart.getItems());
        for (CartItem guestItem : guestItems) {
            Product product = guestItem.getProduct();
            int guestQty = guestItem.getQuantity();

            CartItem existingItem = findCartItem(userCart, product.getId());
            if (existingItem != null) {
                int totalQty = existingItem.getQuantity() + guestQty;
                // Cap merged quantity to available warehouse stock to prevent overflow
                int finalQty = Math.min(totalQty, product.getStockQuantity());
                existingItem.setQuantity(finalQty);
            } else {
                int finalQty = Math.min(guestQty, product.getStockQuantity());
                if (finalQty > 0) {
                    userCart.addProduct(product, finalQty);
                }
            }
        }

        // Delete guest cart after its items have been merged into the user cart
        cartRepository.delete(guestCart);
    }

    private void validateStockAvailability(Product product, int requestedQuantity) {
        if (requestedQuantity > product.getStockQuantity()) {
            log.warn("Insufficient stock for product id={}: requested={}, available={}", product.getId(), requestedQuantity, product.getStockQuantity());
            throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Requested: " + requestedQuantity + ", Available: " + product.getStockQuantity());
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

    private Product findProductOrThrow(Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return product.get();
    }

    private Product findProductWithImagesOrThrow(Long id) {
        Optional<Product> product = productRepository.findByIdWithImages(id);
        if (product.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return product.get();
    }

    private void validateAddToCartRequest(AddToCartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Add to cart request cannot be null.");
        }
        if (request.productId() == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
    }

    private void validateUpdateCartItemRequest(UpdateCartItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update cart item request cannot be null.");
        }
        if (request.quantity() == null) {
            throw new IllegalArgumentException("Quantity cannot be null.");
        }
    }
}
