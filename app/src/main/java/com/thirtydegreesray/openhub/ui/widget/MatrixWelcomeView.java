package com.thirtydegreesray.openhub.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A short greeting in the side menu header, in the style of the Matrix rain behind it:
 * the letters arrive as random green glyphs and are "decoded" one by one into the text,
 * which then glows with a slow pulse and an occasional flicker, breaks up into glyphs
 * again and comes back after a pause.
 *
 * Animates only while on screen; with the system's animations off it just shows the text.
 */
public class MatrixWelcomeView extends View {

    private static final String TEXT = "Welcome to OpenHub";
    private static final int TEXT_COLOR = 0xFFC8FFD4;
    private static final int GLYPH_COLOR = 0x00FF41;
    private static final int GLOW_COLOR = 0xFF00FF41;

    /** Phases of one cycle, in milliseconds. */
    private static final long ARRIVE = 700;
    private static final long DECODE = 1500;
    private static final long HOLD = 5500;
    private static final long DISSOLVE = 900;
    private static final long PAUSE = 1600;
    private static final long CYCLE = DECODE + HOLD + DISSOLVE + PAUSE;
    private static final long GLYPH_CHANGE_MS = 70;
    private static final long FRAME_MS = 33L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final char[] glyphs = new char[TEXT.length()];
    private final char[] oneChar = new char[1];
    private final float baseTextSize;
    private final float glowRadius;

    private long startTime = SystemClock.uptimeMillis();
    private long lastGlyphChange;
    private long flickerUntil;
    private boolean running;

    public MatrixWelcomeView(@NonNull Context context) {
        this(context, null);
    }

    public MatrixWelcomeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MatrixWelcomeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        baseTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics());
        glowRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                getResources().getDisplayMetrics());
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(baseTextSize);
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = randomGlyph();
        setContentDescription(TEXT);
    }

    private char randomGlyph() {
        return MatrixRainView.GLYPHS.charAt(random.nextInt(MatrixRainView.GLYPHS.length()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // shrink the text when the space between the avatar and the logo is narrow
        paint.setTextSize(baseTextSize);
        float needed = paint.measureText(TEXT) + glowRadius * 2;
        if (w > 0 && needed > w) paint.setTextSize(baseTextSize * w / needed);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        boolean wasRunning = running;
        running = isVisible;
        if (running && !wasRunning) {
            // every opening of the menu starts with the decoding
            startTime = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int n = TEXT.length();
        float charW = paint.measureText("M");
        float left = (getWidth() - charW * n) / 2f;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (getHeight() - fm.ascent - fm.descent) / 2f;

        boolean animate = ValueAnimator.areAnimatorsEnabled();
        if (!animate) {
            paint.setColor(TEXT_COLOR);
            paint.setShadowLayer(glowRadius * 0.6f, 0, 0, GLOW_COLOR);
            canvas.drawText(TEXT, left, y, paint);
            return;
        }

        long now = SystemClock.uptimeMillis();
        long t = (now - startTime) % CYCLE;
        if (now - lastGlyphChange >= GLYPH_CHANGE_MS) {
            lastGlyphChange = now;
            for (int i = 0; i < n; i++) glyphs[i] = randomGlyph();
        }

        // a short flicker now and then while the text holds
        boolean holding = t >= DECODE && t < DECODE + HOLD;
        if (holding && now >= flickerUntil && random.nextInt(140) == 0) {
            flickerUntil = now + 60 + random.nextInt(90);
        }
        boolean flicker = holding && now < flickerUntil;
        float pulse = holding
                ? 0.65f + 0.35f * (float) Math.sin((t - DECODE) / 700.0 * Math.PI)
                : 0.5f;

        for (int i = 0; i < n; i++) {
            char real = TEXT.charAt(i);
            if (real == ' ') continue;
            float slot = (float) i / Math.max(1, n - 1);
            char shown;
            int color;
            int alpha;
            if (t < DECODE) {
                long arrives = (long) (slot * ARRIVE);
                long decodes = ARRIVE + (long) (slot * (DECODE - ARRIVE));
                if (t < arrives) continue;
                if (t < decodes) {
                    shown = glyphs[i];
                    color = GLYPH_COLOR;
                    alpha = 200;
                } else {
                    shown = real;
                    color = TEXT_COLOR & 0x00FFFFFF;
                    alpha = 255;
                }
            } else if (t < DECODE + HOLD) {
                shown = real;
                color = TEXT_COLOR & 0x00FFFFFF;
                alpha = flicker ? 90 : 255;
            } else if (t < DECODE + HOLD + DISSOLVE) {
                // breaks up from the right to the left and fades out
                float p = (float) (t - DECODE - HOLD) / DISSOLVE;
                boolean broken = p > 1f - slot * 0.7f - 0.15f;
                shown = broken ? glyphs[i] : real;
                color = broken ? GLYPH_COLOR : TEXT_COLOR & 0x00FFFFFF;
                alpha = (int) (255 * Math.max(0f, 1f - p * p));
            } else {
                continue;
            }
            paint.setColor((alpha << 24) | color);
            if (shown == real) {
                paint.setShadowLayer(glowRadius * pulse, 0, 0, GLOW_COLOR);
            } else {
                paint.clearShadowLayer();
            }
            oneChar[0] = shown;
            float x = left + i * charW;
            canvas.drawText(oneChar, 0, 1, x, y, paint);
        }
        paint.clearShadowLayer();

        if (running && isShown()) postInvalidateDelayed(FRAME_MS);
    }
}
