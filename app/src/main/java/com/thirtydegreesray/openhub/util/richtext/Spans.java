package com.thirtydegreesray.openhub.util.richtext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.ui.activity.VideoPlayerActivity;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;
import com.thirtydegreesray.openhub.util.AppOpener;

/** The small spans of {@link RichText}. */
final class Spans {

    private Spans() {
    }

    /** A link, a picture that opens in the viewer, or a video that opens in the player. */
    static final class Link extends ClickableSpan {

        static final int LINK = 0;
        static final int IMAGE = 1;
        static final int VIDEO = 2;

        final String url;
        final int kind;

        Link(@NonNull String url, int kind) {
            this.url = url;
            this.kind = kind;
        }

        @Override
        public void onClick(@NonNull View widget) {
            Context context = widget.getContext();
            switch (kind) {
                case IMAGE:
                    ViewerActivity.showImage(context, url);
                    break;
                case VIDEO:
                    VideoPlayerActivity.show(context, url);
                    break;
                default:
                    // github.com links open in the app, videos in the player, the rest in the browser
                    AppOpener.launchUrl(context, Uri.parse(url));
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            if (kind == IMAGE) return;
            ds.setColor(ds.linkColor);
            ds.setUnderlineText(false);
        }
    }

    /**
     * The marker of a list item (bullet, number or task box) in the margin; every level of
     * a nested list adds one margin. The marker is drawn only on the item's first line.
     */
    static final class ListItem implements LeadingMarginSpan {

        private final String marker;
        private final int width;

        ListItem(@NonNull String marker, int width) {
            this.marker = marker;
            this.width = width;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return width;
        }

        @Override
        public void drawLeadingMargin(@NonNull Canvas c, @NonNull Paint p, int x, int dir, int top,
                                      int baseline, int bottom, @NonNull CharSequence text, int start,
                                      int end, boolean first, @NonNull Layout layout) {
            if (!(text instanceof Spanned) || ((Spanned) text).getSpanStart(this) != start) return;
            float markerWidth = p.measureText(marker);
            float gap = width * 0.25f;
            float left = dir > 0 ? x + width - gap - markerWidth : x - width + gap;
            c.drawText(marker, left, baseline, p);
        }
    }

    /** A code block: a background over the whole width and some space on the left. */
    static final class CodeBlock implements LeadingMarginSpan, LineBackgroundSpan {

        private final int color;
        private final int padding;

        CodeBlock(@ColorInt int color, int padding) {
            this.color = color;
            this.padding = padding;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return padding;
        }

        @Override
        public void drawLeadingMargin(@NonNull Canvas c, @NonNull Paint p, int x, int dir, int top,
                                      int baseline, int bottom, @NonNull CharSequence text, int start,
                                      int end, boolean first, @NonNull Layout layout) {
        }

        @Override
        public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint, int left, int right,
                                   int top, int baseline, int bottom, @NonNull CharSequence text,
                                   int start, int end, int lineNumber) {
            int old = paint.getColor();
            Paint.Style style = paint.getStyle();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(old);
            paint.setStyle(style);
        }
    }

    /** A horizontal rule ({@code <hr>}): a thin line across the middle of its line. */
    static final class Rule implements LineBackgroundSpan {

        private final int color;
        private final int thickness;

        Rule(@ColorInt int color, int thickness) {
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint, int left, int right,
                                   int top, int baseline, int bottom, @NonNull CharSequence text,
                                   int start, int end, int lineNumber) {
            int old = paint.getColor();
            Paint.Style style = paint.getStyle();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            float middle = (top + bottom) / 2f;
            canvas.drawRect(left, middle - thickness / 2f, right, middle + thickness / 2f, paint);
            paint.setColor(old);
            paint.setStyle(style);
        }
    }
}
