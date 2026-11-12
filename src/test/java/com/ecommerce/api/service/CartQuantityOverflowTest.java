package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.impl.CartServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartQuantityOverflowTest {

    @Mock
    private CartResolver cartResolver;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    @DisplayName("Should reject addition when existing quantity plus request quantity overflows integer limit")
    void shouldThrowInsufficientStockWhenQuantityAdditionOverflowsInteger() {
        Long productId = 1L;
        Product product = Product.builder().id(productId).name("Test Product").price(BigDecimal.valueOf(100)).stockQuantity(10).build();

        Cart cart = Cart.builder().id(10L).items(new ArrayList<>()).build();

        CartItem existingItem = CartItem.builder().id(100L).cart(cart).product(product).quantity(1).build();
        cart.getItems().add(existingItem);

        when(cartResolver.findGuest(any())).thenReturn(Optional.of(cart));
        when(productRepository.getWithImagesOrThrow(productId)).thenReturn(product);

        AddToCartRequest maliciousRequest = new AddToCartRequest(productId, Integer.MAX_VALUE);

        assertThrows(InsufficientStockException.class, () -> cartService.addToCart(null, "guest-token", maliciousRequest));
    }
}
