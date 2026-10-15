package com.ecommerce.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Enterprise decorator for PasswordEncoder implementing Defense-in-Depth.
 * Pre-hashes incoming plaintext passwords using HMAC-SHA256 with a system-wide pepper key,
 * completely mitigating the 72-byte BCrypt truncation vulnerability and protecting against offline DB dumps.
 * Package-private access modifier: restricted to internal security infrastructure.
 */
class PepperedPasswordEncoder implements PasswordEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final PasswordEncoder delegate;
    private final SecretKeySpec secretKeySpec;

    PepperedPasswordEncoder(String pepperKey) {
        this(pepperKey, new BCryptPasswordEncoder());
    }

    PepperedPasswordEncoder(String pepperKey, PasswordEncoder delegate) {
        if (pepperKey == null || pepperKey.trim().length() < 32) {
            throw new IllegalArgumentException("Pepper secret key must be at least 32 characters long for enterprise security.");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate PasswordEncoder cannot be null.");
        }
        this.delegate = delegate;
        byte[] keyBytes = pepperKey.getBytes(StandardCharsets.UTF_8);
        this.secretKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword cannot be null");
        }
        String peppered = applyHmac(rawPassword);
        return delegate.encode(peppered);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword cannot be null");
        }
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        String peppered = applyHmac(rawPassword);
        return delegate.matches(peppered, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    /**
     * Computes HMAC-SHA256 of the raw password using the pre-initialized secret key specification.
     * Generates a fixed 32-byte digest represented as a Base64 string.
     */
    private String applyHmac(CharSequence rawPassword) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Cryptographic failure while calculating password HMAC-SHA256", e);
        }
    }
}
