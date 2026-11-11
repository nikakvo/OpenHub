package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One look for quoted text ("&gt; ...") everywhere, close to github.com: a 4 dp grey bar,
 * 12 dp space before the text, and the quoted text in the secondary text color.
 * Android's own {@code QuoteSpan} (used by {@code Html.fromHtml}) draws a 2 px blue line
 * that is hard to see on the dark themes.
 */
public final class QuoteStyle {

    /** Width of the bar and the space after it, in dp. */
    public static final int BAR_DP = 4;
    public static final int GAP_DP = 12;

    private static final int FALLBACK_SECONDARY = 0xFF8B949E;

    private QuoteStyle() {
    }

    /** The secondary text color of the screen's theme (light on the dark themes). */
    @ColorInt
    public static int textColor(@NonNull Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            ColorStateList list = a.getColorStateList(0);
            return list != null ? list.getDefaultColor() : FALLBACK_SECONDARY;
        } catch (RuntimeException e) {
            return FALLBACK_SECONDARY;
        } finally {
            a.recycle();
        }
    }

    /** The bar: the secondary text color at 55 %, clearly visible on dark and light themes. */
    @ColorInt
    public static int barColor(@NonNull Context context) {
        int color = textColor(context);
        return Color.argb(140, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int px(@NonNull Context context, int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    /**
     * Replaces every {@code QuoteSpan} of {@code text} (from {@code Html.fromHtml}) with the
     * bar of this class and the secondary text color. Other spans stay as they are.
     */
    @Nullable
    public static CharSequence apply(@NonNull Context context, @Nullable CharSequence text) {
        if (!(text instanceof Spanned)) return text;
        Spanned spanned = (Spanned) text;
        QuoteSpan[] quotes = spanned.getSpans(0, spanned.length(), QuoteSpan.class);
        if (quotes.length == 0) return text;
        Spannable out = text instanceof Spannable ? (Spannable) text : new SpannableStringBuilder(text);
        int bar = barColor(context);
        int textColor = textColor(context);
        int barPx = px(context, BAR_DP);
        int gapPx = px(context, GAP_DP);
        for (QuoteSpan quote : quotes) {
            int start = out.getSpanStart(quote);
            int end = out.getSpanEnd(quote);
            int flags = out.getSpanFlags(quote);
            out.removeSpan(quote);
            if (start < 0 || end <= start) continue;
            out.setSpan(new BarSpan(bar, barPx, gapPx), start, end, flags);
            out.setSpan(new ForegroundColorSpan(textColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    /** The bar of a quote; nested quotes get one bar each, side by side. */
    public static final class BarSpan implements LeadingMarginSpan {

        private final int color;
        private final int barWidth;
        private final int gapWidth;

        public BarSpan(@ColorInt int color, int barWidth, int gapWidth) {
            this.color = color;
            this.barWidth = barWidth;
            this.gapWidth = gapWidth;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return barWidth + gapWidth;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                      int bottom, CharSequence text, int start, int end,
                                      boolean first, Layout layout) {
            Paint.Style style = p.getStyle();
            int old = p.getColor();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            float left = x;
            float right = x + dir * barWidth;
            c.drawRect(Math.min(left, right), top, Math.max(left, right), bottom, p);
            p.setStyle(style);
            p.setColor(old);
        }
    }
}
