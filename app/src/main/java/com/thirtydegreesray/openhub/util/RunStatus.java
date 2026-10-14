package com.thirtydegreesray.openhub.util;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.thirtydegreesray.openhub.R;

/**
 * Colour and label of a GitHub Actions status (runs, jobs and steps share them):
 * green success, red failure, amber running or waiting, grey cancelled or skipped.
 */
public final class RunStatus {

    public static final int GREEN = 0xFF2DA44E;
    public static final int RED = 0xFFCF222E;
    public static final int AMBER = 0xFFBF8700;
    public static final int GREY = 0xFF8C959F;

    @ColorInt public final int color;
    @StringRes public final int label;

    private RunStatus(@ColorInt int color, @StringRes int label) {
        this.color = color;
        this.label = label;
    }

    @NonNull
    public static RunStatus of(@Nullable String status, @Nullable String conclusion) {
        if ("completed".equals(status)) {
            String c = conclusion == null ? "" : conclusion;
            switch (c) {
                case "success":
                    return new RunStatus(GREEN, R.string.actions_run_success);
                case "failure":
                    return new RunStatus(RED, R.string.actions_run_failure);
                case "timed_out":
                    return new RunStatus(RED, R.string.actions_run_timed_out);
                case "action_required":
                    return new RunStatus(AMBER, R.string.actions_run_action_required);
                case "skipped":
                case "neutral":
                    return new RunStatus(GREY, R.string.actions_run_skipped);
                default:
                    return new RunStatus(GREY, R.string.actions_run_cancelled);
            }
        }
        if ("in_progress".equals(status)) return new RunStatus(AMBER, R.string.actions_run_in_progress);
        return new RunStatus(AMBER, R.string.actions_run_queued);
    }

    /** "45s", "3m 12s", "1h 04m"; to now when {@code end} is null; "" without a start. */
    @NonNull
    public static String duration(@Nullable java.util.Date start, @Nullable java.util.Date end) {
        if (start == null) return "";
        long ms = (end == null ? System.currentTimeMillis() : end.getTime()) - start.getTime();
        return duration(ms);
    }

    @NonNull
    static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m " + (s % 60) + "s";
        return (m / 60) + "h " + String.format(java.util.Locale.ROOT, "%02dm", m % 60);
    }

    public boolean failed() {
        return color == RED;
    }

    /** A round dot in the status colour as the background of {@code dot}. */
    public void paintDot(@NonNull View dot) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
    }

    public void paintLabel(@NonNull TextView text) {
        text.setText(label);
        text.setTextColor(color);
    }
}
