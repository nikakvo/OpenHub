package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.PopupMenu;

/**
 * A PopupMenu that closes together with its screen (rotation, theme or font change), like
 * {@link ScreenDialog.Builder} for dialogs; otherwise Android removes its window itself and logs
 * "has leaked window". Used for every popup menu of the app.
 */
public class ScreenPopupMenu extends PopupMenu {

    public ScreenPopupMenu(@NonNull Context context, @NonNull View anchor) {
        super(context, anchor);
        ScreenDialog.closeWithScreen(context, this::dismiss);
    }

    public ScreenPopupMenu(@NonNull Context context, @NonNull View anchor, int gravity) {
        super(context, anchor, gravity);
        ScreenDialog.closeWithScreen(context, this::dismiss);
    }

    public ScreenPopupMenu(@NonNull Context context, @NonNull View anchor, int gravity,
                           @AttrRes int popupStyleAttr, @StyleRes int popupStyleRes) {
        super(context, anchor, gravity, popupStyleAttr, popupStyleRes);
        ScreenDialog.closeWithScreen(context, this::dismiss);
    }
}
