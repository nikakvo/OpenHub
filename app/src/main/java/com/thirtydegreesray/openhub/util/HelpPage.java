package com.thirtydegreesray.openhub.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.ui.activity.HelpActivity;

/**
 * The in-app help page (assets/help.html), opened from the side menu.
 */
public final class HelpPage {

    private HelpPage() {
    }

    public static void show(@NonNull Context context) {
        HelpActivity.show(context);
    }
}
