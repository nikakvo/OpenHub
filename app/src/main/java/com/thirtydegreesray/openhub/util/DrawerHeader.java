package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;

/**
 * Background of the side menu header (the account part at the top).
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

    /** Shows or hides the rain in a header inflated from nav_header_main. */
    public static void apply(@NonNull View header) {
        View rain = header.findViewById(R.id.matrix_rain);
        if (rain == null) return;
        rain.setVisibility(STYLE_THEME.equals(getStyle(header.getContext())) ? View.GONE : View.VISIBLE);
    }

    /** For the settings summary. */
    public static int label(@NonNull String style) {
        return STYLE_THEME.equals(style) ? R.string.menu_header_theme : R.string.menu_header_matrix;
    }
}
