

package com.thirtydegreesray.openhub.ui.widget.colorChooser;

import android.content.Context;
import android.graphics.PorterDuff;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ColorGridDialog;
import com.thirtydegreesray.openhub.util.PrefUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 2017/8/4.
 *
 * @author ThirtyDegreesRay
 */

public class ColorChooserPreference extends Preference {

    private ColorChooserCallback colorChooserCallback;
    private int oriColor;

    public ColorChooserPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ColorChooserPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ColorChooserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorChooserPreference(Context context) {
        super(context);
        init();
    }

    public void setColorChooserCallback(ColorChooserCallback colorChooserCallback) {
        this.colorChooserCallback = colorChooserCallback;
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_widget_color);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View colorView = holder.findViewById(R.id.color_view);
        colorView.setBackgroundResource(R.drawable.shape_circle);
        colorView.getBackground().setColorFilter(getSelectedColor(), PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onClick() {
        super.onClick();
        oriColor = getSelectedColor();
        ColorGridDialog.show(BaseActivity.getCurActivity(), R.string.theme_accent_color,
                getAccentColors(), oriColor, this::onColorSelection);
    }

    private void onColorSelection(@ColorInt int selectedColor) {
        PrefUtils.set(PrefUtils.ACCENT_COLOR, getColorIndex(selectedColor));
        if (colorChooserCallback != null && oriColor != selectedColor) {
            colorChooserCallback.onColorChanged(oriColor, selectedColor);
        }
    }



    @NonNull
    private int[] getAccentColors() {
        int[] colorsResId = getContext().getResources().getIntArray(R.array.accent_color_array);
        return colorsResId;
    }

    private int getColorByIndex(int index) {
        return getColorList().get(index);
    }

    private int getColorIndex(int color) {
        return getColorList().indexOf(color);
    }

    private int getSelectedColor() {
        return getColorByIndex(PrefUtils.getAccentColor());
    }

    private List<Integer> getColorList() {
        int[] colorsResId = getContext().getResources().getIntArray(R.array.accent_color_array);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < colorsResId.length; i++) {
            list.add(colorsResId[i]);
        }
        return list;
    }

    public interface ColorChooserCallback {
        void onColorChanged(@ColorInt int oriColor, @ColorInt int selectedColor);
    }
}
