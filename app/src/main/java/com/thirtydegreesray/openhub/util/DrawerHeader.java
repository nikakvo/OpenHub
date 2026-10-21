package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.navigation.NavigationView;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.MatrixRainView;

/**
 * Background of the side menu header (the account part at the top): Matrix rain with a
 * greeting, or the theme color.
 * Settings: Menu header (Matrix rain / Theme color).
 */
public final class DrawerHeader {

    public static final String PREF = "menuHeaderStyle";
    public static final String STYLE_MATRIX = "matrix";
    public static final String STYLE_THEME = "theme";
    public static final String[] STYLES = {STYLE_MATRIX, STYLE_THEME};

    private DrawerHeader() {
    }

    @NonNull
    public static String getStyle(@NonNull Context context) {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(PREF, STYLE_MATRIX);
    }

    /**
     * Puts the Matrix rain behind the menu (the header and a strip at the right edge) and
     * shows the greeting, or leaves the theme colored header. Safe to call again.
     */
    public static void apply(@NonNull NavigationView nav) {
        if (nav.getHeaderCount() == 0) return;
        View header = nav.getHeaderView(0);
        boolean matrix = !STYLE_THEME.equals(getStyle(nav.getContext()));

        MatrixRainView rain = null;
        for (int i = 0; i < nav.getChildCount(); i++) {
            if (nav.getChildAt(i) instanceof MatrixRainView) {
                rain = (MatrixRainView) nav.getChildAt(i);
                break;
            }
        }
        if (matrix && rain == null) {
            rain = new MatrixRainView(nav.getContext());
            // behind the menu list, so the items and the header stay on top
            nav.addView(rain, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (!matrix && rain != null) {
            nav.removeView(rain);
            rain = null;
        }
        if (rain != null) rain.setHeader(header);

        if (matrix) {
            header.setBackground(null);
        } else {
            header.setBackgroundResource(R.drawable.side_nav_bar);
        }
        View welcome = header.findViewById(R.id.matrix_welcome);
        if (welcome != null) welcome.setVisibility(matrix ? View.VISIBLE : View.GONE);
    }

    /** For the settings summary. */
    public static int label(@NonNull String style) {
        return STYLE_THEME.equals(style) ? R.string.menu_header_theme : R.string.menu_header_matrix;
    }
}
