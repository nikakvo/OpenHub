package com.thirtydegreesray.openhub.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.MatrixRainView;
import com.thirtydegreesray.openhub.ui.widget.MatrixWelcomeView;

/**
 * The Matrix look of the side menu, for whole screens (Settings, About, Help): the rain
 * behind the app bar, a large decoding title with a terminal line under it, and on dark
 * themes an almost black screen with one column of rain falling down each edge.
 *
 * Follows Settings - Menu header: with "Theme color" the screen stays as it was.
 */
public final class MatrixScreen {

    /** The same almost black as the side menu with the rain. */
    public static final int BACKGROUND = 0xFF070B08;
    /** Edge columns are softer than in the menu, so they never fight with the text. */
    private static final int EDGE_ALPHA = 45;

    private MatrixScreen() {
    }

    /** True when Settings - Menu header is Matrix rain. */
    public static boolean isOn(@NonNull Context context) {
        return !DrawerHeader.STYLE_THEME.equals(DrawerHeader.getStyle(context));
    }

    /** True when the screen gets the almost black background (Matrix rain on a dark theme). */
    public static boolean isDark(@NonNull Context context) {
        return isOn(context) && AppUtils.isNightMode();
    }

    /**
     * Turns a screen with the usual app bar (layout_appbar: app_bar, toolbar) into a
     * Matrix screen. Does nothing with "Theme color" or when it was done already.
     *
     * @param root     the screen's root layout, the rain goes behind everything in it
     * @param title    decoded in large letters below the toolbar
     * @param subtitle the terminal line below the title (a blinking cursor follows it)
     */
    public static void apply(@NonNull AppCompatActivity activity, @NonNull ViewGroup root,
                             @NonNull String title, @NonNull String subtitle) {
        if (!isOn(activity)) return;
        AppBarLayout appBar = root.findViewById(R.id.app_bar);
        if (appBar == null || appBar.findViewById(R.id.matrix_title) != null) return;
        Toolbar toolbar = appBar.findViewById(R.id.toolbar);
        boolean dark = AppUtils.isNightMode();

        if (dark) {
            root.setBackgroundColor(BACKGROUND);
            EdgeToEdge.setBarColors(activity, Color.BLACK, BACKGROUND);
        } else {
            // light themes keep their background; only the bar above the title is black
            EdgeToEdge.setBarColors(activity, Color.BLACK, EdgeToEdge.navigationBarColor(activity));
        }

        MatrixRainView rain = new MatrixRainView(activity);
        // on light themes green glyphs over the list would only be noise
        rain.setEdgeColumns(dark ? 1 : 0, dark ? 1 : 0, EDGE_ALPHA);
        root.addView(rain, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rain.setHeader(appBar);

        appBar.setBackgroundColor(Color.TRANSPARENT);
        appBar.setStateListAnimator(null);
        ViewCompat.setElevation(appBar, 0f);
        if (toolbar != null) toolbar.setBackgroundColor(Color.TRANSPARENT);
        if (activity.getSupportActionBar() != null) {
            // the large title below says it
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        View block = LayoutInflater.from(activity).inflate(R.layout.layout_matrix_title, appBar, false);
        int index = toolbar != null ? appBar.indexOfChild(toolbar) + 1 : 0;
        appBar.addView(block, index);

        MatrixWelcomeView titleView = block.findViewById(R.id.matrix_title);
        titleView.setTextSizeSp(22);
        titleView.setAlignStart(true);
        titleView.setText(title);
        TextView subtitleView = block.findViewById(R.id.matrix_subtitle);
        subtitleView.setText(subtitle);
        View cursor = block.findViewById(R.id.matrix_cursor);
        if (ValueAnimator.areAnimatorsEnabled()) {
            AlphaAnimation blink = new AlphaAnimation(1f, 0f);
            blink.setDuration(500);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            cursor.startAnimation(blink);
        }
    }
}
