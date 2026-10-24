package com.asifqureshi.aivoicestudio.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared background executor so network and file work never runs on the main thread. */
public final class Background {
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private Background() {}

    public static void run(Runnable r) {
        IO.execute(r);
    }
}
