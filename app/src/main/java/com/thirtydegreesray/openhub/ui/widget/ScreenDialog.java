package com.thirtydegreesray.openhub.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * Dialogs that close together with their screen. When a screen is rebuilt (rotation, theme or
 * font change, Android ending it) a dialog that is still open is otherwise removed by Android
 * itself, which logs "has leaked window". {@link Builder} is used for every AlertDialog of the
 * app, {@link ScreenPopupMenu} for popup menus, {@link #closeWithScreen} for other dialogs.
 * Toolbar overflow menus: {@link com.thirtydegreesray.openhub.util.ScreenMenus}.
 */
public final class ScreenDialog {

    private ScreenDialog() {
    }

    /** An AlertDialog builder whose dialogs close with their screen. */
    public static class Builder extends AlertDialog.Builder {

        public Builder(@NonNull Context context) {
            super(context);
        }

        public Builder(@NonNull Context context, @StyleRes int themeResId) {
            super(context, themeResId);
        }

        @NonNull
        @Override
        public AlertDialog create() {
            AlertDialog dialog = super.create();
            closeWithScreen(getContext(), dialog);
            return dialog;
        }
    }

    /**
     * Dismisses the dialog when the screen of {@code context} is destroyed. The dialog's own
     * dismiss listener is not touched.
     */
    public static void closeWithScreen(@NonNull Context context, @NonNull Dialog dialog) {
        closeWithScreen(context, () -> {
            if (dialog.isShowing()) dialog.dismiss();
        });
    }

    /** Runs {@code close} when the screen of {@code context} is destroyed (popup menus). */
    public static void closeWithScreen(@NonNull Context context, @NonNull Runnable close) {
        LifecycleOwner owner = owner(context);
        if (owner == null) return;
        Lifecycle lifecycle = owner.getLifecycle();
        if (lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) return;
        lifecycle.addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner source) {
                source.getLifecycle().removeObserver(this);
                try {
                    close.run();
                } catch (Exception ignored) {
                    // the window is already gone
                }
            }
        });
    }

    /** The screen behind a (themed) context, or null. */
    @Nullable
    private static LifecycleOwner owner(@Nullable Context context) {
        for (int i = 0; context != null && i < 10; i++) {
            if (context instanceof LifecycleOwner) return (LifecycleOwner) context;
            if (!(context instanceof ContextWrapper)) return null;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
