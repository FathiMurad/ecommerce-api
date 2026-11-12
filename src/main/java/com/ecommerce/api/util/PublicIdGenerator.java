package com.ecommerce.api.util;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Cryptographically strong public identifier generator for order tracking numbers and payment transaction IDs.
 */
public final class PublicIdGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();
    private static final int RANDOM_BYTES_LENGTH = 8; // 64 bits of entropy (16 hex chars)

    private PublicIdGenerator() {
    }

    public static String generateTrackingNumber() {
        return "ORD-" + randomHex();
    }

    public static String generateTransactionId() {
        return "TXN-" + randomHex();
    }

    private static String randomHex() {
        byte[] bytes = new byte[RANDOM_BYTES_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }
}
