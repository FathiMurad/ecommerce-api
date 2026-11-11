package com.ecommerce.api.service;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.security.OrderAccessPolicy;
import com.ecommerce.api.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSecurityIdorTest {

    @Mock
    private OrderRepository orderRepository;

    @Spy
    private OrderAccessPolicy orderAccessPolicy = new OrderAccessPolicy();

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    @DisplayName("User B must receive 404 (ResourceNotFoundException) when accessing User A's order to prevent IDOR oracle")
    void shouldThrowNotFoundWhenUserAccessesAnotherUsersOrder() {
        String trackingNumber = "ORD-TEST123456";
        User owner = User.builder().id(1L).email("owner@test.com").role(Role.ROLE_CUSTOMER).build();
        User attacker = User.builder().id(2L).email("attacker@test.com").role(Role.ROLE_CUSTOMER).build();

        Order order = Order.builder().id(10L).trackingNumber(trackingNumber).user(owner).items(new ArrayList<>()).build();

        when(orderRepository.getByTrackingNumberOrThrow(trackingNumber)).thenReturn(order);

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderByTrackingNumber(trackingNumber, attacker));

        verify(orderAccessPolicy).assertCanAccess(order, attacker);
    }

    @Test
    @DisplayName("Admin must be allowed to access any user's order")
    void shouldAllowAdminToAccessAnyOrder() {
        String trackingNumber = "ORD-TEST123456";
        User owner = User.builder().id(1L).email("owner@test.com").role(Role.ROLE_CUSTOMER).build();
        User admin = User.builder().id(99L).email("admin@test.com").role(Role.ROLE_ADMIN).build();

        Order order = Order.builder().id(10L).trackingNumber(trackingNumber).user(owner).items(new ArrayList<>()).build();

        when(orderRepository.getByTrackingNumberOrThrow(trackingNumber)).thenReturn(order);

        assertDoesNotThrow(() -> orderService.getOrderByTrackingNumber(trackingNumber, admin));
        verify(orderAccessPolicy).assertCanAccess(order, admin);
    }

    @Test
    @DisplayName("Guest should be allowed to access guest order via tracking number")
    void shouldAllowAccessToGuestOrder() {
        String trackingNumber = "ORD-GUEST12345";
        Order guestOrder = Order.builder().id(11L).trackingNumber(trackingNumber).user(null).items(new ArrayList<>()).build();

        when(orderRepository.getByTrackingNumberOrThrow(trackingNumber)).thenReturn(guestOrder);

        assertDoesNotThrow(() -> orderService.getOrderByTrackingNumber(trackingNumber, null));
        verify(orderAccessPolicy).assertCanAccess(guestOrder, null);
    }
}
