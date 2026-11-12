package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * A round picture (avatars). The view is clipped to a circle by its outline, which the
 * system draws in hardware; the picture always fills the circle (center crop).
 * Without a picture (still loading, failed, or held back by "Disable loading network
 * image" on mobile data) it shows a neutral person on a subtle disc instead of nothing.
 */
public class CircleImageView extends AppCompatImageView {

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint personPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shoulders = new RectF();

    private static final ViewOutlineProvider CIRCLE = new ViewOutlineProvider() {
        @Override
        public void getOutline(@NonNull View view, @NonNull Outline outline) {
            int size = Math.min(view.getWidth() - view.getPaddingLeft() - view.getPaddingRight(),
                    view.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
            if (size <= 0) {
                outline.setEmpty();
                return;
            }
            int left = view.getPaddingLeft()
                    + (view.getWidth() - view.getPaddingLeft() - view.getPaddingRight() - size) / 2;
            int top = view.getPaddingTop()
                    + (view.getHeight() - view.getPaddingTop() - view.getPaddingBottom() - size) / 2;
            outline.setOval(left, top, left + size, top + size);
        }
    };

    public CircleImageView(@NonNull Context context) {
        this(context, null);
    }

    public CircleImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setScaleType(ScaleType.CENTER_CROP);
        setOutlineProvider(CIRCLE);
        setClipToOutline(true);
        // the theme's secondary text color: grey on light themes, light grey on dark ones
        int secondary = 0xFF8B949E;
        TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            secondary = a.getColor(0, secondary);
        } catch (RuntimeException ignored) {
            // a color state list the theme cannot resolve here: keep the neutral grey
        } finally {
            a.recycle();
        }
        // an opaque disc (the Matrix rain behind a menu avatar must not show through):
        // the secondary color mixed into black on dark themes, into white on light ones
        boolean darkTheme = (Color.red(secondary) + Color.green(secondary) + Color.blue(secondary)) / 3 > 128;
        discPaint.setColor(mix(darkTheme ? Color.BLACK : Color.WHITE, secondary, 0.22f));
        personPaint.setColor(withAlpha(secondary, 0xB3));
    }

    private static int mix(int base, int color, float amount) {
        return Color.rgb(
                Math.round(Color.red(base) + (Color.red(color) - Color.red(base)) * amount),
                Math.round(Color.green(base) + (Color.green(color) - Color.green(base)) * amount),
                Math.round(Color.blue(base) + (Color.blue(color) - Color.blue(base)) * amount));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (getDrawable() == null) {
            drawPlaceholder(canvas);
            return;
        }
        super.onDraw(canvas);
    }

    /** A head and shoulders on a disc; the circle outline cuts the shoulders off. */
    private void drawPlaceholder(@NonNull Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        if (size <= 0) return;
        float cx = getPaddingLeft() + w / 2f;
        float top = getPaddingTop() + (h - size) / 2f;
        canvas.drawCircle(cx, top + size / 2f, size / 2f, discPaint);
        canvas.drawCircle(cx, top + size * 0.39f, size * 0.17f, personPaint);
        shoulders.set(cx - size * 0.32f, top + size * 0.64f, cx + size * 0.32f, top + size * 1.12f);
        canvas.drawOval(shoulders, personPaint);
    }

    @Override
    public void setScaleType(@NonNull ScaleType scaleType) {
        // the picture must fill the circle
        super.setScaleType(ScaleType.CENTER_CROP);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }
}
