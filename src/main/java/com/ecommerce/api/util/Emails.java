package com.ecommerce.api.util;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
