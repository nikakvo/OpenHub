package com.thirtydegreesray.openhub.util;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.MatrixRainView;
import com.thirtydegreesray.openhub.ui.widget.MatrixTitleDrawable;

/**
 * The Matrix look of the toolbar on every screen (Settings - Menu header = Matrix rain):
 * a black bar with the rain behind the toolbar (and its tabs), a decoding title
 * ({@link MatrixTitleDrawable}) and a black status bar. Screens with a collapsing header
 * (profile, repository, issue, commit) keep their header; only the bar with the menu,
 * Back and ⋮ buttons and the status bar above it become black with the rain.
 *
 * Settings, About and Help have the larger look of {@link MatrixScreen} and are skipped.
 * With "Theme color" nothing changes.
 */
public final class MatrixBar {

    private static final int COLLAPSED_TITLE_COLOR = 0xFFC8FFD4;

    private MatrixBar() {
    }

    public static void install(@NonNull AppCompatActivity activity, @NonNull Toolbar toolbar) {
        if (!MatrixScreen.isOn(activity)) return;
        AppBarLayout appBar = findAppBar(toolbar);
        if (appBar == null || appBar.getTag(R.id.matrix_screen_tag) != null) return;
        appBar.setTag(R.id.matrix_screen_tag, Boolean.TRUE);

        EdgeToEdge.setBarColors(activity, Color.BLACK, EdgeToEdge.navigationBarColor(activity));
        MatrixRainView rain = new MatrixRainView(activity);
        rain.setEdgeColumns(0, 0, 0);
        rain.setShade(false);
        toolbar.setBackgroundColor(Color.TRANSPARENT);

        ViewParent parent = toolbar.getParent();
        if (parent instanceof CollapsingToolbarLayout) {
            CollapsingToolbarLayout header = (CollapsingToolbarLayout) parent;
            // over the header image, under the toolbar; from the top of the window, so the
            // status bar is black too; pinned, so it stays with the toolbar
            rain.setFitsSystemWindows(true);
            rain.setFromTop(true);
            rain.setHeader(toolbar);
            CollapsingToolbarLayout.LayoutParams lp = new CollapsingToolbarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setCollapseMode(CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN);
            header.addView(rain, header.indexOfChild(toolbar), lp);
            // the scrims would cover the rain when the header collapses
            header.setContentScrim(null);
            header.setStatusBarScrim(null);
            header.setCollapsedTitleTextColor(COLLAPSED_TITLE_COLOR);
            header.setCollapsedTitleTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            // only when the toolbar shows its own title (usually the header draws it)
            MatrixTitleDrawable.attach(toolbar);
            return;
        }

        // behind the whole screen, it paints only behind the app bar
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View root = content.getChildCount() > 0 ? content.getChildAt(0) : null;
        content.addView(rain, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rain.setHeader(appBar);
        appBar.setBackgroundColor(Color.TRANSPARENT);
        appBar.setStateListAnimator(null);
        ViewCompat.setElevation(appBar, 0f);
        if (root instanceof DrawerLayout) {
            // the start screen draws its own status bar
            ((DrawerLayout) root).setStatusBarBackgroundColor(Color.BLACK);
        }
        MatrixTitleDrawable.attach(toolbar);
    }

    /** Decodes the toolbar title again (the screen came back to the front). */
    public static void restart(@Nullable Toolbar toolbar) {
        MatrixTitleDrawable.restart(toolbar);
    }

    @Nullable
    private static AppBarLayout findAppBar(@NonNull View view) {
        ViewParent p = view.getParent();
        while (p instanceof View) {
            if (p instanceof AppBarLayout) return (AppBarLayout) p;
            p = p.getParent();
        }
        return null;
    }
}
