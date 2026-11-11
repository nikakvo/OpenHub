package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * A round picture (avatars). The view is clipped to a circle by its outline, which the
 * system draws in hardware; the picture always fills the circle (center crop).
 */
public class CircleImageView extends AppCompatImageView {

    private static final ViewOutlineProvider CIRCLE = new ViewOutlineProvider() {
        @Override
        public void getOutline(@NonNull View view, @NonNull Outline outline) {
            int size = Math.min(view.getWidth() - view.getPaddingLeft() - view.getPaddingRight(),
                    view.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
            if (size <= 0) {
                outline.setEmpty();
                return;
            }
            int left = view.getPaddingLeft()
                    + (view.getWidth() - view.getPaddingLeft() - view.getPaddingRight() - size) / 2;
            int top = view.getPaddingTop()
                    + (view.getHeight() - view.getPaddingTop() - view.getPaddingBottom() - size) / 2;
            outline.setOval(left, top, left + size, top + size);
        }
    };

    public CircleImageView(@NonNull Context context) {
        this(context, null);
    }

    public CircleImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setScaleType(ScaleType.CENTER_CROP);
        setOutlineProvider(CIRCLE);
        setClipToOutline(true);
    }

    @Override
    public void setScaleType(@NonNull ScaleType scaleType) {
        // the picture must fill the circle
        super.setScaleType(ScaleType.CENTER_CROP);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }
}
