package com.asifqureshi.aivoicestudio.utils;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure-Java input validation (unit tested without Android). */
public final class Validators {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[^\\s@]{2,}$");

    private Validators() {}

    public static boolean isValidEmail(String s) {
        return s != null && EMAIL.matcher(s.trim()).matches();
    }

    /** Mirrors the backend rule: 8-128 chars with at least one letter and one digit. */
    public static boolean isStrongPassword(String s) {
        if (s == null || s.length() < 8 || s.length() > 128) return false;
        boolean letter = false, digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    public static boolean isValidName(String s) {
        return s != null && s.trim().length() >= 1 && s.trim().length() <= 80;
    }

    private static boolean isLocalHost(String host) {
        return host.equals("localhost") || host.equals("10.0.2.2") || host.equals("127.0.0.1");
    }

    /**
     * Returns a normalised base URL with a trailing slash, or null when invalid.
     * HTTPS is required; plain HTTP is only accepted for local development hosts.
     */
    public static String normalizeServerUrl(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        try {
            URI u = new URI(s);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost();
            if (host == null || host.isEmpty()) return null;
            boolean ok = scheme.equals("https") || (scheme.equals("http") && isLocalHost(host.toLowerCase(Locale.ROOT)));
            if (!ok) return null;
            if (u.getRawQuery() != null || u.getRawFragment() != null || u.getRawUserInfo() != null) return null;
            return s.endsWith("/") ? s : s + "/";
        } catch (Exception e) {
            return null;
        }
    }
}
