package com.thirtydegreesray.openhub.util;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.thirtydegreesray.openhub.R;

/**
 * The look of the buttons made in code (the actions under comments: Reply, Resolve
 * conversation, upvotes, Mark as answer, poll options), the same as the button styles of
 * the layouts (OpenHubButton): rounded pills that sink a little while pressed.
 */
public final class ButtonStyles {

    private ButtonStyles() {
    }

    /** Secondary action: accent-tinted pill with a thin outline, accent text. */
    public static void tonal(@NonNull TextView view) {
        apply(view, R.drawable.btn_tonal, R.color.btn_accent_text);
    }

    /** Main or active action (e.g. your upvote): pill in the accent color, white text. */
    public static void filled(@NonNull TextView view) {
        apply(view, R.drawable.btn_filled, R.color.btn_filled_text);
    }

    /** Only the pressed effect and the rounded ripple, for a row that keeps its own look. */
    public static void press(@NonNull View view) {
        view.setStateListAnimator(AnimatorInflater.loadStateListAnimator(view.getContext(), R.animator.btn_press));
    }

    private static void apply(@NonNull TextView view, int background, int textColor) {
        Context context = view.getContext();
        view.setBackgroundResource(background);
        view.setTextColor(ContextCompat.getColorStateList(context, textColor));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 34));
        view.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        press(view);
        view.setClickable(true);
    }

    /** Space between pills in a row. */
    public static void spaceInRow(@NonNull View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        LinearLayout.LayoutParams params = lp instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) lp
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(view.getContext(), 8));
        params.topMargin = dp(view.getContext(), 4);
        params.bottomMargin = dp(view.getContext(), 4);
        view.setLayoutParams(params);
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
