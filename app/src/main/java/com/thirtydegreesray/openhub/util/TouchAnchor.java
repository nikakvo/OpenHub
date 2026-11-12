package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;

import com.thirtydegreesray.openhub.R;

/**
 * Popup menus of long cards (comments) open where the finger is. Anchored to the card
 * itself, a menu opened under the card's top edge: with only the bottom of a long comment
 * on the screen the menu was off screen and the long-press seemed to do nothing.
 */
public final class TouchAnchor {

    /** A long press is at most this old when the menu opens. */
    private static final long MAX_AGE_MS = 5000L;

    private static float downX = Float.NaN;
    private static float downY = Float.NaN;
    private static long downTime;

    private TouchAnchor() {
    }

    /** Called by BaseActivity for every touch: remembers where the finger went down. */
    public static void record(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            downTime = SystemClock.uptimeMillis();
        }
    }

    /**
     * A popup menu (dark on the dark themes) at the last touch-down point when it lies on
     * {@code card}; otherwise anchored to the card as before.
     */
    @NonNull
    public static PopupMenu popup(@NonNull Activity activity, @NonNull View card) {
        View anchor = anchorAt(activity, card);
        PopupMenu menu = new PopupMenu(activity, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (anchor != card) {
            menu.setOnDismissListener(m -> {
                if (anchor.getParent() instanceof ViewGroup) {
                    ((ViewGroup) anchor.getParent()).removeView(anchor);
                }
            });
        }
        return menu;
    }

    @NonNull
    private static View anchorAt(@NonNull Activity activity, @NonNull View card) {
        if (Float.isNaN(downX) || SystemClock.uptimeMillis() - downTime > MAX_AGE_MS) return card;
        int[] pos = new int[2];
        card.getLocationOnScreen(pos);
        if (downX < pos[0] || downX > pos[0] + card.getWidth()
                || downY < pos[1] || downY > pos[1] + card.getHeight()) {
            return card;
        }
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return card;
        int[] origin = new int[2];
        decor.getLocationOnScreen(origin);
        View anchor = new View(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(downX - origin[0]);
        lp.topMargin = Math.round(downY - origin[1]);
        ((FrameLayout) decor).addView(anchor, lp);
        // the popup needs the anchor laid out: measure and place it now
        anchor.measure(View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY));
        anchor.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + 1, lp.topMargin + 1);
        return anchor;
    }
}
