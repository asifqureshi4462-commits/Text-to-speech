package com.asifqureshi.aivoicestudio.audio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.asifqureshi.aivoicestudio.R;

/**
 * Decorative animated bars used on the splash screen. This is purely a visual flourish:
 * it does NOT analyse any audio and is hidden from accessibility services.
 */
public class WaveformView extends View {
    private static final int BARS = 21;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float phase;

    public WaveformView(Context c) { this(c, null); }

    public WaveformView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        paint.setShader(new LinearGradient(0, 0, w, 0, ContextCompat.getColor(getContext(), R.color.brand_cyan),
                ContextCompat.getColor(getContext(), R.color.brand_violet), Shader.TileMode.CLAMP));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(1400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float slot = w / BARS, barW = slot * 0.5f;
        for (int i = 0; i < BARS; i++) {
            float envelope = 1f - Math.abs(i - BARS / 2f) / (BARS / 2f) * 0.6f;
            float amp = (0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase + i * 0.7f))) * envelope;
            float bh = Math.max(barW, h * amp);
            float cx = slot * i + slot / 2f;
            rect.set(cx - barW / 2f, (h - bh) / 2f, cx + barW / 2f, (h + bh) / 2f);
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
        }
    }
}
