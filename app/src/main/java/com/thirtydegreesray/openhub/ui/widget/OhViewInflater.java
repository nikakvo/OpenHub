package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatViewInflater;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * Puts the app's text fields ({@link OhEditText}, {@link OhTextInputEditText}) in place of
 * every EditText of the layouts, so each one has the select / clear mark. Set as
 * {@code viewInflaterClass} in the themes.
 */
public class OhViewInflater extends AppCompatViewInflater {

    @NonNull
    @Override
    protected AppCompatEditText createEditText(Context context, AttributeSet attrs) {
        return new OhEditText(context, attrs);
    }

    @Nullable
    @Override
    protected View createView(Context context, String name, AttributeSet attrs) {
        if ("com.google.android.material.textfield.TextInputEditText".equals(name)) {
            return new OhTextInputEditText(context, attrs);
        }
        return super.createView(context, name, attrs);
    }
}
