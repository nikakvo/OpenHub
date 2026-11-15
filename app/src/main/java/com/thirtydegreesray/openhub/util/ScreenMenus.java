package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

/**
 * Closes the open overflow menu (three dots) of every toolbar of a screen, and an open text
 * drop-down (the search history) and the long-press menu of a list, before the screen saves
 * its state or goes away. Without this a menu that is open while the phone is rotated leaks
 * its window ("has leaked window"), and the toolbar opens it again on the new screen.
 * The menus close at once because the app themes give them no fade-out (OhPopupMenu in
 * styles.xml): with a fade-out Android keeps the window until the fade has ended.
 */
public final class ScreenMenus implements Application.ActivityLifecycleCallbacks {

    private ScreenMenus() {
    }

    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new ScreenMenus());
    }

    /** Closes the toolbar menus and text drop-downs (search history) in this view tree. */
    public static void closeToolbarMenus(@Nullable View view) {
        if (view instanceof Toolbar) {
            ((Toolbar) view).dismissPopupMenus();
        } else if (view instanceof AutoCompleteTextView && ((AutoCompleteTextView) view).isPopupShowing()) {
            ((AutoCompleteTextView) view).dismissDropDown();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) closeToolbarMenus(group.getChildAt(i));
        }
    }

    private static void close(@NonNull Activity activity) {
        try {
            if (activity.getWindow() != null) closeToolbarMenus(activity.getWindow().peekDecorView());
        } catch (Exception ignored) {
            // no window
        }
        try {
            // the long-press menu of the news list (a context menu)
            activity.closeContextMenu();
        } catch (Exception ignored) {
            // no window
        }
    }

    /** Android 10+: before the state is saved, so the toolbar saves "menu closed". */
    @Override
    public void onActivityPreSaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        close(activity);
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        close(activity);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
}
