package com.thirtydegreesray.openhub.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * Collapsing headers (repository, profile, issue, commit) open smoothly after a fast fling.
 *
 * When a quick swipe down reaches the top of the page, the rest of the fling went into the
 * header, which then jumped open by its whole height in about three frames: it looked like a
 * flicker. Now the fling stops at the top of the page and the header opens with a short
 * animation instead. Dragging with the finger moves the header as before.
 */
public final class SmoothHeader {

    private SmoothHeader() {
    }

    /** For a screen whose app bar holds a collapsing header; other screens are left alone. */
    public static void install(@Nullable CollapsingToolbarLayout header) {
        if (header == null || !(header.getParent() instanceof AppBarLayout)) return;
        AppBarLayout appBar = (AppBarLayout) header.getParent();
        ViewGroup.LayoutParams lp = appBar.getLayoutParams();
        if (!(lp instanceof CoordinatorLayout.LayoutParams)) return;
        CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) lp;
        if (clp.getBehavior() instanceof Behavior) return;
        clp.setBehavior(new Behavior());
        appBar.setLayoutParams(clp);
    }

    static final class Behavior extends AppBarLayout.Behavior {

        @Override
        public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                   @NonNull AppBarLayout child, @NonNull View target,
                                   int dxConsumed, int dyConsumed, int dxUnconsumed,
                                   int dyUnconsumed, int type, @NonNull int[] consumed) {
            if (type == ViewCompat.TYPE_NON_TOUCH && dyUnconsumed < 0) {
                // a fling reached the top of the page: open the header with an animation
                // instead of the remaining fling
                consumed[1] += dyUnconsumed;
                if (getTopAndBottomOffset() < 0) child.setExpanded(true, true);
                if (target instanceof RecyclerView) {
                    ((RecyclerView) target).stopScroll();
                } else {
                    ViewCompat.stopNestedScroll(target, ViewCompat.TYPE_NON_TOUCH);
                }
                return;
            }
            super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed,
                    dxUnconsumed, dyUnconsumed, type, consumed);
        }
    }
}
