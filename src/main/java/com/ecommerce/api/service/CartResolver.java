package com.ecommerce.api.service;

import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared component responsible for resolving, claiming, and merging user and guest shopping carts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartResolver {

    private final CartRepository cartRepository;

    public Optional<Cart> findGuest(String cartToken) {
        if (cartToken == null || cartToken.isBlank()) {
            return Optional.empty();
        }
        Optional<Cart> existingCart = cartRepository.findByCartTokenWithItems(cartToken.trim());
        if (existingCart.isPresent()) {
            Cart cart = existingCart.get();
            if (cart.getUser() == null) {
                return Optional.of(cart);
            }
            log.warn("Anonymous access attempt to authenticated user cart via token.");
        }
        return Optional.empty();
    }

    public Optional<Cart> findForUser(User user, String cartToken) {
        Optional<Cart> userCartOpt = cartRepository.findByUserIdWithItems(user.getId());
        String trimmedToken = (cartToken != null && !cartToken.isBlank()) ? cartToken.trim() : null;

        if (userCartOpt.isPresent()) {
            Cart userCart = userCartOpt.get();
            if (trimmedToken != null) {
                mergeGuestCartIfExists(userCart, trimmedToken);
            }
            return Optional.of(userCart);
        }

        if (trimmedToken != null) {
            return claimGuestCartIfExists(user, trimmedToken);
        }

        return Optional.empty();
    }

    public Cart createCart(User ownerOrNull) {
        Cart newCart = Cart.builder().cartToken(UUID.randomUUID().toString()).user(ownerOrNull).items(new ArrayList<>()).build();
        return cartRepository.save(newCart);
    }

    private void mergeGuestCartIfExists(Cart userCart, String guestCartToken) {
        cartRepository.findByCartTokenWithItems(guestCartToken).ifPresent(guestCart -> {
            if (guestCart.getUser() == null && !guestCart.getId().equals(userCart.getId())) {
                log.info("Merging guest cart into user cart id={} for user id={}", userCart.getId(), userCart.getUser().getId());
                mergeItems(userCart, guestCart);
                cartRepository.delete(guestCart);
            }
        });
    }

    private Optional<Cart> claimGuestCartIfExists(User user, String guestCartToken) {
        return cartRepository.findByCartTokenWithItems(guestCartToken).filter(guestCart -> guestCart.getUser() == null).map(guestCart -> {
            log.info("Claiming anonymous guest cart for user id={}", user.getId());
            guestCart.setUser(user);
            return guestCart;
        });
    }

    private void mergeItems(Cart userCart, Cart guestCart) {
        List<CartItem> guestItems = new ArrayList<>(guestCart.getItems());
        for (CartItem guestItem : guestItems) {
            Product product = guestItem.getProduct();
            int guestQty = guestItem.getQuantity();

            CartItem existingItem = findCartItem(userCart, product.getId());
            if (existingItem != null) {
                long totalQty = (long) existingItem.getQuantity() + guestQty;
                int finalQty = (int) Math.min(totalQty, (long) product.getStockQuantity());
                if (finalQty <= 0) {
                    userCart.removeProduct(product.getId());
                } else {
                    existingItem.setQuantity(finalQty);
                }
            } else {
                int finalQty = Math.min(guestQty, product.getStockQuantity());
                if (finalQty > 0) {
                    userCart.addProduct(product, finalQty);
                }
            }
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
}
