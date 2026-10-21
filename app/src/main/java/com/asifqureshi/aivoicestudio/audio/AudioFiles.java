package com.asifqureshi.aivoicestudio.audio;

import android.content.Context;

import java.io.File;

/** Private cache for generated/downloaded audio. Files live in app-private storage and can be cleared by the user. */
public final class AudioFiles {
    private AudioFiles() {}

    public static File cacheDir(Context c) {
        File d = new File(c.getCacheDir(), "audio");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static long cacheSize(Context c) {
        long total = 0;
        File[] files = cacheDir(c).listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    public static void clear(Context c) {
        File[] files = cacheDir(c).listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
