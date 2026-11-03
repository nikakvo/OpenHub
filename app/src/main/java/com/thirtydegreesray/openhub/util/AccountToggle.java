package com.thirtydegreesray.openhub.util;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.thirtydegreesray.openhub.R;

/**
 * The arrow next to the name in the side menu (Add account, Log out, switch accounts).
 * Over the Matrix rain the small white arrow was hard to see, so it sits on a round
 * background: in the accent color under the Matrix rain, in the color of the name (with a
 * darker or lighter disc) on the theme colored header. Every time the side menu opens it
 * pulses a few times.
 */
public final class AccountToggle {

    private static final int SIZE_DP = 34;
    private static final int ICON_PADDING_DP = 5;
    /** Up and down three times. */
    private static final int PULSES = 3;

    private AccountToggle() {
    }

    /** Colors and size for the current menu header style; call again when the menu opens. */
    public static void style(@NonNull ImageView toggle, @Nullable TextView name) {
        Context context = toggle.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        boolean matrix = !DrawerHeader.STYLE_THEME.equals(DrawerHeader.getStyle(context));

        @ColorInt int fg;
        @ColorInt int fill;
        if (matrix) {
            fg = ViewUtils.getAccentColor(context);
            fill = ColorUtils.setAlphaComponent(fg, 60);
        } else {
            // the header's own text color, so it is as easy to see as the name
            fg = name != null ? name.getCurrentTextColor() : Color.WHITE;
            boolean lightText = ColorUtils.calculateLuminance(fg) > 0.5;
            fill = lightText ? Color.argb(70, 0, 0, 0) : Color.argb(90, 255, 255, 255);
        }

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fill);
        circle.setStroke(Math.max(1, Math.round(1.5f * density)), fg);
        toggle.setBackground(circle);
        ImageViewCompat.setImageTintList(toggle, ColorStateList.valueOf(fg));
        int padding = Math.round(ICON_PADDING_DP * density);
        toggle.setPadding(padding, padding, padding, padding);
        ViewGroup.LayoutParams lp = toggle.getLayoutParams();
        if (lp != null) {
            int size = Math.round(SIZE_DP * density);
            if (lp.width != size || lp.height != size) {
                lp.width = size;
                lp.height = size;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                    m.topMargin = 0;
                    m.bottomMargin = 0;
                    m.setMarginStart(Math.round(8 * density));
                    m.setMarginEnd(Math.round(2 * density));
                }
                toggle.setLayoutParams(lp);
            }
        }
        toggle.setContentDescription(context.getString(R.string.manage_accounts));
        toggle.setVisibility(View.VISIBLE);
    }

    /** The menu was opened: a few gentle pulses. */
    public static void pulse(@NonNull ImageView toggle) {
        stop(toggle);
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(toggle,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f));
        pulse.setDuration(450);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(PULSES * 2 - 1);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        toggle.setTag(R.id.toggle_account_bn, pulse);
        pulse.start();
    }

    /** The menu was closed or the arrow tapped. */
    public static void stop(@NonNull ImageView toggle) {
        Object tag = toggle.getTag(R.id.toggle_account_bn);
        if (tag instanceof ObjectAnimator && ((ObjectAnimator) tag).isStarted()) {
            ((ObjectAnimator) tag).cancel();
        }
        toggle.setScaleX(1f);
        toggle.setScaleY(1f);
    }
}
