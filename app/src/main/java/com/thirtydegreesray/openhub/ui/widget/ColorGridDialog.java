package com.thirtydegreesray.openhub.ui.widget;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.WindowUtil;

/**
 * Simple color picker: a grid of round swatches, tap one to pick it.
 * Replaces the ColorChooserDialog that was copied from material-dialogs.
 */
public class ColorGridDialog {

    public interface OnColorSelectedListener {
        void onColorSelected(@ColorInt int color);
    }

    private ColorGridDialog() {
    }

    public static void show(@NonNull Activity activity, @StringRes int title,
                            @NonNull final int[] colors, @ColorInt final int selectedColor,
                            @NonNull final OnColorSelectedListener listener) {
        final int size = WindowUtil.dipToPx(activity, 48);
        final int spacing = WindowUtil.dipToPx(activity, 12);
        final int ringWidth = WindowUtil.dipToPx(activity, 3);
        final int ringColor = ViewUtils.getTitleColor(activity);

        GridView grid = new GridView(activity);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(size);
        grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
        grid.setHorizontalSpacing(spacing);
        grid.setVerticalSpacing(spacing);
        grid.setPadding(spacing * 2, spacing, spacing * 2, spacing);
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(grid)
                .setNegativeButton(R.string.cancel, null)
                .create();

        grid.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return colors.length;
            }

            @Override
            public Object getItem(int position) {
                return colors[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View swatch = convertView;
                if (swatch == null) {
                    swatch = new View(parent.getContext());
                    swatch.setLayoutParams(new GridView.LayoutParams(size, size));
                }
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(colors[position]);
                if (colors[position] == selectedColor) {
                    circle.setStroke(ringWidth, ringColor);
                }
                swatch.setBackground(circle);
                swatch.setContentDescription("#" + ViewUtils.getRGBColor(colors[position], false));
                return swatch;
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            listener.onColorSelected(colors[position]);
            dialog.dismiss();
        });
        dialog.show();
    }

}
