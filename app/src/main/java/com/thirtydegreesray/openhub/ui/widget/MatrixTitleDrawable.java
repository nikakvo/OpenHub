package com.thirtydegreesray.openhub.ui.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;

import com.thirtydegreesray.openhub.R;

import java.util.Random;

/**
 * The toolbar title in the Matrix style: drawn over the toolbar's own title (which is made
 * transparent but stays for TalkBack), its letters arrive as green glyphs and are decoded
 * one by one, then the title stays with a soft glow and a short flicker now and then.
 * A new title (another section of the side menu) is decoded again, and so is the title
 * when the screen comes back ({@link #restart}). The subtitle gets a green terminal font.
 *
 * Unlike the large title of Settings, About and Help, the toolbar title does not break up
 * and come back in a loop: on lists it has to stay readable.
 */
public final class MatrixTitleDrawable extends android.graphics.drawable.Drawable
        implements ViewTreeObserver.OnPreDrawListener {

    private static final int TEXT_COLOR = 0xFFC8FFD4;
    private static final int GLYPH_COLOR = 0x00FF41;
    private static final int GLOW_COLOR = 0xFF00FF41;
    public static final int SUBTITLE_COLOR = 0xFF8FD9A8;

    private static final long ARRIVE = 500;
    private static final long DECODE = 1200;
    private static final long GLYPH_CHANGE_MS = 70;
    private static final long FRAME_MS = 33L;
    /** Smallest size a long title is shrunk to before it is cut with an ellipsis. */
    private static final float MIN_SCALE = 0.72f;

    private final Toolbar toolbar;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Rect area = new Rect();
    private final char[] oneChar = new char[1];
    private final float glowRadius;
    private final Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    private String title = "";
    /** The title as it fits (maybe cut). */
    private String shown = "";
    private char[] glyphs = new char[0];
    private float textSize;
    private long startTime;
    private long lastGlyphChange;
    private long nextFlicker;
    private long flickerUntil;

    private MatrixTitleDrawable(@NonNull Toolbar toolbar) {
        this.toolbar = toolbar;
        glowRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                toolbar.getResources().getDisplayMetrics());
        paint.setTypeface(mono);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Puts the Matrix title on the toolbar (once). */
    public static void attach(@NonNull Toolbar toolbar) {
        if (toolbar.getTag(R.id.matrix_title_tag) != null) return;
        MatrixTitleDrawable drawable = new MatrixTitleDrawable(toolbar);
        toolbar.setTag(R.id.matrix_title_tag, drawable);
        toolbar.setTitleTextColor(Color.TRANSPARENT);
        toolbar.setSubtitleTextColor(SUBTITLE_COLOR);
        toolbar.getOverlay().add(drawable);
        toolbar.getViewTreeObserver().addOnPreDrawListener(drawable);
        toolbar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(drawable);
            }
        });
    }

    /** Decodes the title of this toolbar again (the screen came back). */
    public static void restart(@Nullable Toolbar toolbar) {
        if (toolbar == null) return;
        Object tag = toolbar.getTag(R.id.matrix_title_tag);
        if (tag instanceof MatrixTitleDrawable) ((MatrixTitleDrawable) tag).restart();
    }

    private void restart() {
        startTime = SystemClock.uptimeMillis();
        nextFlicker = 0;
        invalidateSelf();
    }

    /** Follows the toolbar's title view: its text, place and size. */
    @Override
    public boolean onPreDraw() {
        TextView titleView = null;
        TextView subtitleView = null;
        CharSequence t = toolbar.getTitle();
        CharSequence st = toolbar.getSubtitle();
        int menuLeft = toolbar.getWidth() - toolbar.getPaddingEnd();
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView && child.getWidth() > 0) {
                menuLeft = Math.min(menuLeft, child.getLeft());
            } else if (child instanceof TextView && child.getVisibility() == View.VISIBLE) {
                CharSequence text = ((TextView) child).getText();
                if (titleView == null && !TextUtils.isEmpty(t) && TextUtils.equals(text, t)) {
                    titleView = (TextView) child;
                } else if (subtitleView == null && !TextUtils.isEmpty(st) && TextUtils.equals(text, st)) {
                    subtitleView = (TextView) child;
                }
            }
        }
        if (subtitleView != null && subtitleView.getTypeface() != Typeface.MONOSPACE) {
            subtitleView.setTypeface(Typeface.MONOSPACE);
        }

        String newTitle = titleView == null ? "" : t.toString();
        int left = 0, top = 0, right = 0, bottom = 0;
        if (titleView != null && titleView.getWidth() > 0) {
            left = titleView.getLeft();
            top = titleView.getTop();
            bottom = titleView.getBottom();
            right = Math.max(titleView.getRight(), menuLeft);
        }
        float size = titleView == null ? textSize : titleView.getTextSize();
        boolean changed = !newTitle.equals(title);
        if (changed || size != textSize || area.left != left || area.top != top
                || area.right != right || area.bottom != bottom) {
            area.set(left, top, right, bottom);
            setBounds(area);
            textSize = size;
            if (changed) {
                title = newTitle;
                startTime = SystemClock.uptimeMillis();
                nextFlicker = 0;
            }
            fit();
            invalidateSelf();
        }
        return true;
    }

    /** Shrinks a long title a little, then cuts it with an ellipsis. */
    private void fit() {
        paint.setTextSize(textSize);
        float width = area.width() - glowRadius;
        shown = title;
        if (width > 0 && !title.isEmpty()) {
            float needed = paint.measureText(title);
            if (needed > width) {
                float scale = Math.max(MIN_SCALE, width / needed);
                paint.setTextSize(textSize * scale);
                if (paint.measureText(title) > width) {
                    int n = Math.max(1, (int) (width / paint.measureText("M")) - 1);
                    shown = title.substring(0, Math.min(title.length(), n)) + "…";
                }
            }
        }
        if (glyphs.length != shown.length()) glyphs = new char[shown.length()];
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = randomGlyph();
    }

    private char randomGlyph() {
        return MatrixRainView.GLYPHS.charAt(random.nextInt(MatrixRainView.GLYPHS.length()));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int n = shown.length();
        if (n == 0 || area.isEmpty()) return;
        float charW = paint.measureText("M");
        float left = area.left;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = area.top + (area.height() - fm.ascent - fm.descent) / 2f;

        if (!ValueAnimator.areAnimatorsEnabled()) {
            paint.setColor(TEXT_COLOR);
            paint.setShadowLayer(glowRadius * 0.6f, 0, 0, GLOW_COLOR);
            canvas.drawText(shown, left, y, paint);
            paint.clearShadowLayer();
            return;
        }

        long now = SystemClock.uptimeMillis();
        long t = now - startTime;
        boolean decoding = t < DECODE;
        if (decoding && now - lastGlyphChange >= GLYPH_CHANGE_MS) {
            lastGlyphChange = now;
            for (int i = 0; i < n; i++) glyphs[i] = randomGlyph();
        }
        if (!decoding && nextFlicker == 0) nextFlicker = now + 4000 + random.nextInt(8000);
        if (!decoding && now >= nextFlicker && now >= flickerUntil) {
            flickerUntil = now + 60 + random.nextInt(90);
            nextFlicker = now + 4000 + random.nextInt(8000);
        }
        boolean flicker = !decoding && now < flickerUntil;

        for (int i = 0; i < n; i++) {
            char real = shown.charAt(i);
            if (real == ' ') continue;
            float slot = (float) i / Math.max(1, n - 1);
            char c;
            int color;
            int alpha;
            if (decoding) {
                long arrives = (long) (slot * ARRIVE);
                long decodes = ARRIVE + (long) (slot * (DECODE - ARRIVE));
                if (t < arrives) continue;
                if (t < decodes) {
                    c = glyphs[i];
                    color = GLYPH_COLOR;
                    alpha = 200;
                } else {
                    c = real;
                    color = TEXT_COLOR & 0x00FFFFFF;
                    alpha = 255;
                }
            } else {
                c = real;
                color = TEXT_COLOR & 0x00FFFFFF;
                alpha = flicker ? 90 : 255;
            }
            paint.setColor((alpha << 24) | color);
            if (c == real) {
                paint.setShadowLayer(glowRadius * 0.7f, 0, 0, GLOW_COLOR);
            } else {
                paint.clearShadowLayer();
            }
            oneChar[0] = c;
            canvas.drawText(oneChar, 0, 1, left + i * charW, y, paint);
        }
        paint.clearShadowLayer();

        // frames only while something moves: the decoding, the flicker, the next flicker
        if (decoding || flicker) {
            toolbar.postInvalidateDelayed(FRAME_MS);
        } else {
            toolbar.postInvalidateDelayed(Math.max(FRAME_MS, nextFlicker - now));
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
