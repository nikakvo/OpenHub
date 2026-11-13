package com.thirtydegreesray.openhub.util.richtext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.MetricAffectingSpan;
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

    /**
     * Bold text. A plain {@code StyleSpan(BOLD)} asks the font for its bold face, and some
     * system fonts (HyperOS) answer with a face that draws exactly like the regular one,
     * so bold and headings looked thin. When the bold face measures the same as the
     * regular one, the text is drawn with a fake bold stroke instead.
     */
    static final class Bold extends MetricAffectingSpan {

        private static final java.util.Map<Typeface, Boolean> REAL_BOLD = new java.util.WeakHashMap<>();

        @Override
        public void updateDrawState(@NonNull TextPaint tp) {
            apply(tp);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint tp) {
            apply(tp);
        }

        private static void apply(@NonNull TextPaint paint) {
            Typeface old = paint.getTypeface() != null ? paint.getTypeface() : Typeface.DEFAULT;
            boolean italic = (old.getStyle() & Typeface.ITALIC) != 0;
            Typeface bold = android.os.Build.VERSION.SDK_INT >= 28
                    ? Typeface.create(old, 700, italic)
                    : Typeface.create(old, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
            paint.setTypeface(bold);
            if (!drawsBold(old, bold, paint.getTextSize())) paint.setFakeBoldText(true);
        }

        /** Draws a short word both ways and compares the ink: the width alone can differ while the strokes do not. */
        private static boolean drawsBold(@NonNull Typeface regular, @NonNull Typeface bold, float size) {
            synchronized (REAL_BOLD) {
                Boolean known = REAL_BOLD.get(regular);
                if (known != null) return known;
                boolean real = ink(bold) > ink(regular) * 1.15f;
                REAL_BOLD.put(regular, real);
                return real;
            }
        }

        private static int ink(@NonNull Typeface typeface) {
            Paint probe = new Paint(Paint.ANTI_ALIAS_FLAG);
            probe.setTextSize(48f);
            probe.setTypeface(typeface);
            probe.setColor(0xff000000);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(160, 64,
                    android.graphics.Bitmap.Config.ALPHA_8);
            new Canvas(bitmap).drawText("lIll", 4, 52, probe);
            int sum = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 160; x++) sum += android.graphics.Color.alpha(bitmap.getPixel(x, y));
            }
            bitmap.recycle();
            return sum;
        }
    }

    /** A link, a picture that opens in the viewer, or a video that opens in the player. */
    static final class Link extends ClickableSpan {

        static final int LINK = 0;
        static final int IMAGE = 1;
        static final int VIDEO = 2;
        /** a table: {@code url} holds its HTML; it opens on its own page, where it scrolls sideways */
        static final int TABLE = 3;

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
                case TABLE:
                    ViewerActivity.showMdSource(context,
                            context.getString(com.thirtydegreesray.openhub.R.string.rich_text_table), url);
                    break;
                default:
                    // github.com links open in the app, videos in the player, the rest in the browser
                    AppOpener.launchUrl(context, Uri.parse(url));
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            if (kind == IMAGE || kind == TABLE) return;
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

    /**
     * A code block: a background over the whole width, some space on the left and a copy
     * mark in the top right corner. {@link RichText} copies the code on a tap on the mark.
     */
    static final class CodeBlock implements LeadingMarginSpan, LineBackgroundSpan,
            android.text.style.LineHeightSpan {

        private final int color;
        private final int padding;
        private final int markColor;
        private final int markSize;
        private final float stroke;
        /** where the copy mark was drawn, in the coordinates of the text layout; empty before */
        final android.graphics.Rect mark = new android.graphics.Rect();

        CodeBlock(@ColorInt int color, int padding, @ColorInt int markColor, int markSize, float stroke) {
            this.color = color;
            this.padding = padding;
            this.markColor = markColor;
            this.markSize = markSize;
            this.stroke = stroke;
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

        /** The first line gets a strip on top for the copy mark, so the mark never covers code. */
        @Override
        public void chooseHeight(@NonNull CharSequence text, int start, int end, int spanstartv,
                                 int lineHeight, @NonNull Paint.FontMetricsInt fm) {
            if (markSize <= 0 || !(text instanceof Spanned) || ((Spanned) text).getSpanStart(this) != start) {
                return;
            }
            int strip = markSize + padding;
            fm.ascent -= strip;
            fm.top -= strip;
        }

        @Override
        public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint, int left, int right,
                                   int top, int baseline, int bottom, @NonNull CharSequence text,
                                   int start, int end, int lineNumber) {
            int old = paint.getColor();
            Paint.Style style = paint.getStyle();
            float width = paint.getStrokeWidth();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRect(left, top, right, bottom, paint);
            if (markSize > 0 && text instanceof Spanned && ((Spanned) text).getSpanStart(this) == start) {
                drawMark(canvas, paint, right - padding - markSize, top + padding / 2);
            }
            paint.setColor(old);
            paint.setStyle(style);
            paint.setStrokeWidth(width);
        }

        /** Two overlapping sheets, like GitHub's copy button. */
        private void drawMark(@NonNull Canvas canvas, @NonNull Paint paint, int x, int y) {
            mark.set(x, y, x + markSize, y + markSize);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(markColor);
            float sheet = markSize * 0.68f;
            float radius = markSize * 0.12f;
            float h = stroke / 2;
            canvas.drawRoundRect(x + markSize - sheet + h, y + markSize - sheet + h, x + markSize - h,
                    y + markSize - h, radius, radius, paint);
            // the back sheet shows only where the front one does not cover it
            float back = markSize - sheet;
            canvas.drawLine(x + h, y + h + radius, x + h, y + sheet - h, paint);
            canvas.drawLine(x + h + radius, y + h, x + sheet - h, y + h, paint);
            canvas.drawLine(x + h, y + sheet - h, x + back, y + sheet - h, paint);
            canvas.drawLine(x + sheet - h, y + h, x + sheet - h, y + back, paint);
            canvas.drawArc(x + h, y + h, x + h + 2 * radius, y + h + 2 * radius, 180, 90, false, paint);
        }

        /** Whether a touch at {@code x, y} (text layout coordinates) is on the copy mark. */
        boolean hitsMark(int x, int y, int slop) {
            return !mark.isEmpty() && x >= mark.left - slop && x <= mark.right + slop
                    && y >= mark.top - slop && y <= mark.bottom + slop;
        }
    }

    /**
     * The thin line under a big heading (h1, h2), like github.com: the last line of the
     * heading gets some space below it and the line is drawn at its bottom.
     */
    static final class HeadingRule implements LineBackgroundSpan, android.text.style.LineHeightSpan {

        private final int color;
        private final int thickness;
        private final int gap;

        HeadingRule(@ColorInt int color, int thickness, int gap) {
            this.color = color;
            this.thickness = thickness;
            this.gap = gap;
        }

        private boolean lastLine(@NonNull CharSequence text, int end) {
            return text instanceof Spanned && end >= ((Spanned) text).getSpanEnd(this);
        }

        @Override
        public void chooseHeight(@NonNull CharSequence text, int start, int end, int spanstartv,
                                 int lineHeight, @NonNull Paint.FontMetricsInt fm) {
            if (!lastLine(text, end)) return;
            fm.descent += gap + thickness;
            fm.bottom += gap + thickness;
        }

        @Override
        public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint, int left, int right,
                                   int top, int baseline, int bottom, @NonNull CharSequence text,
                                   int start, int end, int lineNumber) {
            if (!lastLine(text, end)) return;
            int old = paint.getColor();
            Paint.Style style = paint.getStyle();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRect(left, bottom - thickness, right, bottom, paint);
            paint.setColor(old);
            paint.setStyle(style);
        }
    }

    /**
     * A key ({@code <kbd>}): monospace text in a small box with a thin border and rounded
     * corners, like github.com. The key never breaks over two lines.
     */
    static final class Kbd extends android.text.style.ReplacementSpan {

        private final int background;
        private final int border;
        private final float stroke;
        private final float radius;
        private final int padding;
        private final android.graphics.RectF box = new android.graphics.RectF();

        Kbd(@ColorInt int background, @ColorInt int border, float stroke, float radius, int padding) {
            this.background = background;
            this.border = border;
            this.stroke = stroke;
            this.radius = radius;
            this.padding = padding;
        }

        @NonNull
        private TextPaint keyPaint(@NonNull Paint paint) {
            TextPaint key = new TextPaint(paint);
            key.setTypeface(Typeface.MONOSPACE);
            key.setTextSize(paint.getTextSize() * 0.85f);
            return key;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           @androidx.annotation.Nullable Paint.FontMetricsInt fm) {
            if (fm != null) paint.getFontMetricsInt(fm);
            float width = keyPaint(paint).measureText(text, start, end);
            // a little space on both sides, so two keys in a row do not touch
            return Math.round(width + 2 * padding + 2 * stroke + padding);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
            TextPaint key = keyPaint(paint);
            float width = key.measureText(text, start, end);
            Paint.FontMetrics m = key.getFontMetrics();
            float left = x + padding / 2f + stroke / 2f;
            box.set(left, y + m.ascent - padding / 2f, left + width + 2 * padding + stroke,
                    y + m.descent + padding / 2f);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(background);
            canvas.drawRoundRect(box, radius, radius, fill);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(stroke);
            fill.setColor(border);
            canvas.drawRoundRect(box, radius, radius, fill);
            canvas.drawText(text, start, end, left + stroke / 2f + padding, y, key);
        }
    }

    /**
     * A small icon in the text (the octicon of an alert title), a bit bigger than the
     * letters, centered on them, with a gap after it.
     */
    static final class Icon extends android.text.style.ReplacementSpan {

        private final android.graphics.drawable.Drawable drawable;

        Icon(@NonNull android.graphics.drawable.Drawable drawable) {
            this.drawable = drawable;
        }

        private static int side(@NonNull Paint paint) {
            return Math.round(paint.getTextSize() * 1.15f);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           @androidx.annotation.Nullable Paint.FontMetricsInt fm) {
            if (fm != null) paint.getFontMetricsInt(fm);
            return side(paint) + Math.round(paint.getTextSize() * 0.55f);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
            int side = side(paint);
            Paint.FontMetrics m = paint.getFontMetrics();
            float middle = y + (m.ascent + m.descent) / 2f;
            int left = Math.round(x);
            int iconTop = Math.round(middle - side / 2f);
            drawable.setBounds(left, iconTop, left + side, iconTop + side);
            drawable.draw(canvas);
        }
    }

    /** The summary line of a {@code <details>}: a tap on it opens or closes it ({@link RichText}). */
    static final class Toggle {

        final int id;
        final boolean open;

        Toggle(int id, boolean open) {
            this.id = id;
            this.open = open;
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
