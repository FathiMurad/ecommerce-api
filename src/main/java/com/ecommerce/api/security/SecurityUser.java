package com.ecommerce.api.security;

import com.ecommerce.api.entity.User;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Adapts the application {@link User} entity to Spring Security's {@link UserDetails} interface.
 */
public record SecurityUser(User user) implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    public User getUser() {
        return user;
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        if (user.getRole() == null) {
            return Collections.emptyList();
        }

        String roleName = user.getRole().name();
        String authorityName = roleName.startsWith(ROLE_PREFIX) ? roleName : ROLE_PREFIX + roleName;
        return List.of(new SimpleGrantedAuthority(authorityName));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public @NonNull String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonLocked() {
        return isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SecurityUser that = (SecurityUser) o;
        return Objects.equals(getUsername(), that.getUsername());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername());
    }
}
