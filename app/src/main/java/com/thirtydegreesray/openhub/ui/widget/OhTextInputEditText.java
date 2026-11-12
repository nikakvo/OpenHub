package com.thirtydegreesray.openhub.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A Material text field with the select / clear mark of {@link SelectClear}. */
public class OhTextInputEditText extends com.google.android.material.textfield.TextInputEditText {

    @Nullable
    private SelectClear selectClear;

    public OhTextInputEditText(@NonNull Context context) {
        super(context);
        selectClear = new SelectClear(this);
    }

    public OhTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        selectClear = new SelectClear(this);
    }

    public OhTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        selectClear = new SelectClear(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (selectClear != null) selectClear.update();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (selectClear != null) selectClear.update();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        if (selectClear != null) selectClear.update();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectClear != null) selectClear.update();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selectClear != null && selectClear.onTouch(event)) return true;
        return super.onTouchEvent(event);
    }
}
