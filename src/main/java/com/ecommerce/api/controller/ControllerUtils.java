package com.ecommerce.api.controller;

import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;

/**
 * Package-private utility methods shared across REST controllers.
 */
final class ControllerUtils {

    private ControllerUtils() {
    }

    static User extractUser(SecurityUser securityUser) {
        return securityUser != null ? securityUser.getUser() : null;
    }
}
