package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.Toasts;

/**
 * The select / clear mark at the end of every text field of the app: it shows while the
 * field has the focus and holds text. The first tap selects the whole text (to copy or
 * replace it), a second tap while everything is selected clears it. Two taps, so one slip
 * of the finger never throws a text away. Used by {@link OhEditText} and
 * {@link OhTextInputEditText}, which the app's view inflater puts in place of every
 * EditText.
 */
final class SelectClear {

    private final EditText view;
    private final Drawable select;
    private final Drawable clear;
    private final int slop;
    /** the field had its own end drawable or an end icon: leave it alone */
    private boolean off;
    private boolean checked;
    @Nullable
    private Drawable shown;
    private boolean pressed;

    SelectClear(@NonNull EditText view) {
        this.view = view;
        Context context = view.getContext();
        int size = Math.round(20 * context.getResources().getDisplayMetrics().density);
        int color = secondaryText(context);
        select = tinted(context, R.drawable.ic_select_all, color, size);
        clear = tinted(context, R.drawable.ic_close, color, size);
        slop = Math.round(10 * context.getResources().getDisplayMetrics().density);
    }

    /** Called on focus, text and selection changes. */
    void update() {
        if (!checked && view.isAttachedToWindow()) {
            checked = true;
            off = hasOwnEnd();
        }
        if (off || !checked) return;
        boolean show = view.isEnabled() && view.isFocused() && view.length() > 0
                && view.getKeyListener() != null;
        Drawable want = show ? (allSelected() ? clear : select) : null;
        if (want == shown) return;
        shown = want;
        Drawable[] d = view.getCompoundDrawablesRelative();
        view.setCompoundDrawablesRelative(d[0], d[1], want, d[3]);
    }

    /** Returns true when the touch was on the mark and is handled here. */
    boolean onTouch(@NonNull MotionEvent event) {
        if (shown == null) return false;
        int action = event.getActionMasked();
        boolean hit = hits(event.getX(), event.getY());
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = hit;
            return hit;
        }
        if (!pressed) return false;
        if (action == MotionEvent.ACTION_UP) {
            pressed = false;
            if (hit) act();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) pressed = false;
        return true;
    }

    private void act() {
        if (view.length() > 0 && allSelected()) {
            view.setText("");
            Toasts.info(view.getContext(), view.getContext().getString(R.string.write_cleared)).show();
        } else {
            // the moving end of the selection at the start: a long text stays at its top
            view.setSelection(view.length(), 0);
        }
        update();
    }

    private boolean hits(float x, float y) {
        Drawable d = shown;
        if (d == null) return false;
        int w = d.getBounds().width();
        int h = d.getBounds().height();
        boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float left = rtl ? view.getCompoundPaddingLeft() - w - slop
                : view.getWidth() - view.getCompoundPaddingRight() - slop;
        float right = rtl ? view.getCompoundPaddingLeft() + slop
                : view.getWidth() - view.getCompoundPaddingRight() + w + slop;
        int space = view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom();
        float top = view.getCompoundPaddingTop() + (space - h) / 2f;
        return x >= left && x <= right && y >= top - slop && y <= top + h + slop;
    }

    private boolean allSelected() {
        int a = view.getSelectionStart();
        int b = view.getSelectionEnd();
        return Math.min(a, b) == 0 && Math.max(a, b) == view.length();
    }

    /** An end drawable of its own, an end icon of its box, or a field with its own button. */
    private boolean hasOwnEnd() {
        int id = view.getId();
        if (id == R.id.md_edit || id == R.id.markdown_edit) return true;
        if (view.getCompoundDrawablesRelative()[2] != null) return true;
        Drawable[] plain = view.getCompoundDrawables();
        if (plain[0] != null || plain[2] != null) return true;
        ViewParent parent = view.getParent();
        for (int i = 0; i < 3 && parent != null; i++, parent = parent.getParent()) {
            if (parent instanceof TextInputLayout) {
                TextInputLayout box = (TextInputLayout) parent;
                return box.getEndIconMode() != TextInputLayout.END_ICON_NONE || box.getSuffixText() != null;
            }
        }
        return false;
    }

    @NonNull
    private static Drawable tinted(@NonNull Context context, int res, int color, int size) {
        Drawable d = DrawableCompat.wrap(ContextCompat.getDrawable(context, res).mutate());
        DrawableCompat.setTint(d, color);
        d.setBounds(0, 0, size, size);
        return d;
    }

    private static int secondaryText(@NonNull Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            return a.getColor(0, 0xFF888888);
        } finally {
            a.recycle();
        }
    }
}
