package com.ecommerce.api.util;

import java.util.Locale;

/**
 * Normalizes email addresses for consistent lookups and storage.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
