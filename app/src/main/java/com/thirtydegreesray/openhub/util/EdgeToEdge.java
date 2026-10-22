package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge handling for every screen (mandatory from targetSdk 36).
 *
 * The content view is padded by the navigation bar, display cutout and keyboard, and the
 * padding strips are painted (status bar: colorPrimaryDark, navigation bar: the window
 * background), which looks like the old opaque system bars. Layouts whose root sets
 * android:fitsSystemWindows (drawer, collapsing toolbars) still draw behind the status
 * bar themselves: for them only the top inset is passed on.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    public static void apply(@NonNull Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        // The keyboard is handled through the IME inset below, never by panning.
        window.setSoftInputMode((window.getAttributes().softInputMode
                & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        int statusColor = resolveColor(activity, androidx.appcompat.R.attr.colorPrimaryDark,
                android.R.attr.colorBackground);
        int navColor = navigationBarColor(activity);
        final BarsDrawable bars = new BarsDrawable(statusColor, navColor);
        content.setBackground(bars);

        applyBarAppearance(activity, statusColor, navColor);

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            View root = content.getChildCount() > 0 ? content.getChildAt(0) : null;
            boolean rootHandlesTop = root != null && root.getFitsSystemWindows();

            int top = rootHandlesTop ? 0 : system.top;
            int bottom = Math.max(system.bottom, ime.bottom);
            v.setPadding(system.left, top, system.right, bottom);
            bars.setStrips(system.left, top, system.right, bottom);

            // Children only get the status bar inset, and only when they draw behind it.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, rootHandlesTop ? system.top : 0, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.captionBar(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .build();
        });
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Sets the system bar icons again (light icons on dark bars, dark icons on light bars).
     * Called when a screen comes back to the front: the system or the ROM (HyperOS) can
     * change the icon color while another window (dialog, other app) had the focus.
     */
    public static void refreshBars(@NonNull Activity activity) {
        if (activity.findViewById(android.R.id.content) == null) return;
        int statusColor = resolveColor(activity, androidx.appcompat.R.attr.colorPrimaryDark,
                android.R.attr.colorBackground);
        int navColor = navigationBarColor(activity);
        applyBarAppearance(activity, statusColor, navColor);
    }

    /**
     * Paints the status and navigation bar strips in other colors, for a screen with its own
     * background (the Matrix screens: black above the title, almost black below).
     */
    public static void setBarColors(@NonNull Activity activity, @ColorInt int statusColor,
                                    @ColorInt int navColor) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || !(content.getBackground() instanceof BarsDrawable)) return;
        BarsDrawable bars = (BarsDrawable) content.getBackground();
        bars.statusPaint.setColor(statusColor);
        bars.navPaint.setColor(navColor);
        bars.invalidateSelf();
        applyBarAppearance(activity, statusColor, navColor);
    }

    private static void applyBarAppearance(@NonNull Activity activity, @ColorInt int statusColor,
                                           @ColorInt int navColor) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The navigation bar strip is painted by BarsDrawable: no system scrim on top.
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(ColorUtils.calculateLuminance(statusColor) > 0.5);
        controller.setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(navColor) > 0.5);
    }

    /**
     * Hides or shows the status bar (full screen code / image viewer). It comes back
     * temporarily with a swipe.
     */
    public static void setStatusBarHidden(@NonNull Activity activity, boolean hidden) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars());
        }
    }

    /**
     * The navigation bar strip uses the window background of the theme. The dark themes
     * are built on a light parent and do not override android:colorBackground, so that
     * attribute is light there and is only a fallback.
     */
    @ColorInt
    static int navigationBarColor(@NonNull Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.windowBackground, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return resolveColor(context, android.R.attr.colorBackground,
                android.R.attr.colorBackground);
    }

    @ColorInt
    private static int resolveColor(@NonNull Context context, @AttrRes int attr,
                                    @AttrRes int fallbackAttr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            context.getTheme().resolveAttribute(fallbackAttr, value, true);
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return 0xFF000000;
    }

    /** Paints the padding strips of the content view in the system bar colors. */
    private static final class BarsDrawable extends Drawable {
        private final Paint statusPaint = new Paint();
        private final Paint navPaint = new Paint();
        private int left, top, right, bottom;

        BarsDrawable(@ColorInt int statusColor, @ColorInt int navColor) {
            statusPaint.setColor(statusColor);
            navPaint.setColor(navColor);
        }

        void setStrips(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (top > 0) canvas.drawRect(b.left, b.top, b.right, b.top + top, statusPaint);
            if (bottom > 0) canvas.drawRect(b.left, b.bottom - bottom, b.right, b.bottom, navPaint);
            if (left > 0) canvas.drawRect(b.left, b.top + top, b.left + left, b.bottom - bottom, navPaint);
            if (right > 0) canvas.drawRect(b.right - right, b.top + top, b.right, b.bottom - bottom, navPaint);
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

}
