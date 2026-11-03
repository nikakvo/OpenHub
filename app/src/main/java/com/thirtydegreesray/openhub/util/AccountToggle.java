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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * The arrow next to the name in the side menu (Add account, Log out, switch accounts).
 * Over the Matrix rain the small white arrow was hard to see, so it is bigger, in the
 * accent color on a round tinted background, and it pulses gently while the side menu is
 * open until it is tapped for the first time.
 */
public final class AccountToggle {

    private static final String PREF_SEEN = "account_toggle_seen";
    private static final int SIZE_DP = 40;
    private static final int ICON_PADDING_DP = 6;

    private AccountToggle() {
    }

    public static void install(@NonNull ImageView toggle, @Nullable DrawerLayout drawer) {
        Context context = toggle.getContext();
        int accent = ViewUtils.getAccentColor(context);
        float density = context.getResources().getDisplayMetrics().density;

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(56, Color.red(accent), Color.green(accent), Color.blue(accent)));
        circle.setStroke(Math.max(1, Math.round(1.5f * density)), accent);
        toggle.setBackground(circle);
        ImageViewCompat.setImageTintList(toggle, ColorStateList.valueOf(accent));
        int padding = Math.round(ICON_PADDING_DP * density);
        toggle.setPadding(padding, padding, padding, padding);
        ViewGroup.LayoutParams lp = toggle.getLayoutParams();
        if (lp != null) {
            lp.width = Math.round(SIZE_DP * density);
            lp.height = Math.round(SIZE_DP * density);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                m.topMargin = 0;
                m.bottomMargin = 0;
                m.setMarginEnd(Math.round(4 * density));
                m.setMarginStart(Math.round(8 * density));
            }
            toggle.setLayoutParams(lp);
        }
        toggle.setContentDescription(context.getString(com.thirtydegreesray.openhub.R.string.manage_accounts));

        if (drawer == null || seen(context)) return;
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(toggle,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f));
        pulse.setDuration(650);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                if (!seen(context) && !pulse.isStarted()) pulse.start();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                stop(toggle, pulse);
            }
        });
        toggle.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                stop(toggle, pulse);
            }
        });
        toggle.setTag(com.thirtydegreesray.openhub.R.id.toggle_account_bn, pulse);
    }

    /** The user found the arrow: no more pulsing, ever. */
    public static void tapped(@NonNull ImageView toggle) {
        PrefUtils.set(PREF_SEEN, true);
        Object tag = toggle.getTag(com.thirtydegreesray.openhub.R.id.toggle_account_bn);
        if (tag instanceof ObjectAnimator) stop(toggle, (ObjectAnimator) tag);
    }

    private static void stop(@NonNull View toggle, @NonNull ObjectAnimator pulse) {
        if (pulse.isStarted()) pulse.cancel();
        toggle.setScaleX(1f);
        toggle.setScaleY(1f);
    }

    private static boolean seen(@NonNull Context context) {
        try {
            return PrefUtils.getDefaultSp(context).getBoolean(PREF_SEEN, false);
        } catch (Exception e) {
            return true;
        }
    }
}
