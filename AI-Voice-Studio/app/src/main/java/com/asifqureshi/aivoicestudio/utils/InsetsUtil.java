package com.asifqureshi.aivoicestudio.utils;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge helpers: pad a view by the system bars (and keyboard) so content is never obscured. */
public final class InsetsUtil {
    private InsetsUtil() {}

    public static void applySystemBars(View view, boolean top, boolean bottom) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = bottom ? Math.max(bars.bottom, ime.bottom) : (ime.bottom > 0 ? ime.bottom : 0);
            v.setPadding(l + bars.left, t + (top ? bars.top : 0), r + bars.right, b + bottomInset);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
