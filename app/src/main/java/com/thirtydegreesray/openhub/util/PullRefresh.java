package com.thirtydegreesray.openhub.util;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Pull down to reload, for screens that were built without a SwipeRefreshLayout.
 * {@link #wrap} puts the screen's scrolling view into a SwipeRefreshLayout in place (same
 * position, same layout params, so CoordinatorLayout behaviors stay on it).
 *
 * While a pull is going, every GET to GitHub asks the server again instead of taking a
 * cached answer ({@link #wantsFreshData()}, used by AppRetrofit), so a pull shows what is on
 * GitHub right now, not what the cache kept from a minute ago.
 *
 * The circle stops with {@link #done()}, or by itself when the screen's own loader
 * ({@link #follow}) has gone away again, and always after {@link #MAX_SPIN_MS}.
 */
public final class PullRefresh {

    /** How long requests started by a pull skip the cache. */
    static final long FRESH_WINDOW_MS = 8_000L;
    /** The circle never turns longer than this, whatever happens. */
    static final long MAX_SPIN_MS = 20_000L;
    /** A loader that does not show within this time means the reload needed none. */
    static final long LOADER_WAIT_MS = 600L;

    private static volatile long freshUntil;

    private final SwipeRefreshLayout layout;
    private final Runnable reload;
    @Nullable private View loader;
    private boolean loaderSeen;
    private long startedAt;
    private float loaderAlpha = 1f;
    private final Runnable stopper = this::done;
    @Nullable private ViewTreeObserver.OnPreDrawListener watcher;

    private PullRefresh(@NonNull SwipeRefreshLayout layout, @NonNull Runnable reload) {
        this.layout = layout;
        this.reload = reload;
    }

    /** True while requests should ask GitHub again instead of the cache. */
    public static boolean wantsFreshData() {
        return SystemClock.elapsedRealtime() < freshUntil;
    }

    /** For reloads that are not started by a pull but must not see cached data either. */
    public static void freshFromNow() {
        freshUntil = SystemClock.elapsedRealtime() + FRESH_WINDOW_MS;
    }

    /**
     * Puts {@code target} (a scrolling view) into a new SwipeRefreshLayout at its place.
     * A target without a parent (a fragment's root) is just added; use {@link #layout()}
     * as the new root then.
     */
    @NonNull
    public static PullRefresh wrap(@NonNull View target, @NonNull Runnable reload) {
        SwipeRefreshLayout srl = new SwipeRefreshLayout(target.getContext()) {
            // screens with tabs hide the scrolling view, not this wrapper: a wrapper whose
            // view is hidden must let the touches through to the tab below it
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (target.getVisibility() != View.VISIBLE) return false;
                return super.dispatchTouchEvent(ev);
            }
        };
        ViewGroup parent = (ViewGroup) target.getParent();
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (parent != null) {
            int index = parent.indexOfChild(target);
            parent.removeViewAt(index);
            parent.addView(srl, index, params);
        } else if (params != null) {
            srl.setLayoutParams(params);
        }
        srl.addView(target, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        srl.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(target.getContext()));
        PullRefresh pull = new PullRefresh(srl, reload);
        srl.setOnRefreshListener(pull::onPulled);
        return pull;
    }

    /**
     * The circle stops when this loader (a ProgressBar the screen shows while loading) is
     * hidden again. The loader itself stays invisible during a pull: one circle is enough.
     */
    @NonNull
    public PullRefresh follow(@Nullable View loader) {
        this.loader = loader;
        return this;
    }

    /**
     * The view whose scroll position decides whether a pull may start (only at the top).
     * Needed when the scrolling view is not the direct child, e.g. a WebView in a frame.
     */
    @NonNull
    public PullRefresh scrollTarget(@NonNull View view) {
        layout.setOnChildScrollUpCallback((parent, child) -> view.canScrollVertically(-1));
        return this;
    }

    /** Stops pulls for a while (e.g. while a list is empty or a tab cannot reload). */
    public void setEnabled(boolean enabled) {
        layout.setEnabled(enabled);
    }

    @NonNull
    public SwipeRefreshLayout layout() {
        return layout;
    }

    public boolean isRefreshing() {
        return layout.isRefreshing();
    }

    /** Starts a reload as if the user had pulled (shows the circle). */
    public void start() {
        layout.post(() -> {
            layout.setRefreshing(true);
            onPulled();
        });
    }

    private void onPulled() {
        freshFromNow();
        startedAt = SystemClock.elapsedRealtime();
        loaderSeen = false;
        layout.removeCallbacks(stopper);
        layout.postDelayed(stopper, MAX_SPIN_MS);
        if (loader != null) {
            loaderAlpha = loader.getAlpha() == 0f ? 1f : loader.getAlpha();
            loader.setAlpha(0f);
            watchLoader();
        }
        try {
            reload.run();
        } catch (RuntimeException e) {
            done();
            throw e;
        }
    }

    private void watchLoader() {
        removeWatcher();
        watcher = () -> {
            View l = loader;
            if (l == null) return true;
            boolean shown = l.getVisibility() == View.VISIBLE && l.isAttachedToWindow();
            if (shown) {
                loaderSeen = true;
            } else if (loaderSeen
                    || SystemClock.elapsedRealtime() - startedAt > LOADER_WAIT_MS) {
                layout.post(stopper);
            }
            return true;
        };
        layout.getViewTreeObserver().addOnPreDrawListener(watcher);
    }

    private void removeWatcher() {
        if (watcher != null) {
            ViewTreeObserver vto = layout.getViewTreeObserver();
            if (vto.isAlive()) vto.removeOnPreDrawListener(watcher);
            watcher = null;
        }
    }

    /** Stops the circle (safe to call any time, also when no pull is going). */
    public void done() {
        layout.removeCallbacks(stopper);
        removeWatcher();
        if (loader != null && loader.getAlpha() == 0f) loader.setAlpha(loaderAlpha);
        if (layout.isRefreshing()) layout.post(() -> layout.setRefreshing(false));
    }
}
