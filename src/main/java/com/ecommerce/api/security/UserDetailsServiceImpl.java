package com.ecommerce.api.security;

import com.ecommerce.api.entity.User;
import com.ecommerce.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Loads user details by normalized email address for Spring Security authentication.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        if (email.isBlank()) {
            throw new UsernameNotFoundException("Authentication failed: Email address cannot be empty.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("User not found with email: " + normalizedEmail);
        }

        return new SecurityUser(userOptional.get());
    }
}
