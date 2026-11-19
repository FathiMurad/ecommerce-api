package com.ecommerce.api.util;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates cryptographically strong, unpredictable public identifiers for order tracking numbers and transaction IDs.
 */
public final class PublicIdGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();
    private static final int TRACKING_BYTES_LENGTH = 16;   // 128 bits of entropy (32 hex chars)
    private static final int TRANSACTION_BYTES_LENGTH = 8; // 64 bits of entropy (16 hex chars)

    private PublicIdGenerator() {
    }

    public static String generateTrackingNumber() {
        return "ORD-" + randomHex(TRACKING_BYTES_LENGTH);
    }

    public static String generateTransactionId() {
        return "TXN-" + randomHex(TRANSACTION_BYTES_LENGTH);
    }

    private static String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }
}
