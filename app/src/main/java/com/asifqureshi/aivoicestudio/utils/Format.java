package com.asifqureshi.aivoicestudio.utils;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class Format {
    private Format() {}

    /** 83000 -> "1:23", 3723000 -> "1:02:03". */
    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    public static String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static String dateTime(long epochMs) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(epochMs));
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase(Locale.getDefault());
    }

    public static String extensionFor(String mime) {
        return mime != null && mime.contains("ogg") ? "opus" : "mp3";
    }
}
