package com.thirtydegreesray.openhub.util.richtext;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One row of a table: the cells side by side in equal columns, each wrapped in its column,
 * with lines between them and a background for the header row.
 */
final class TableRowSpan extends ReplacementSpan {

    private final RichDoc doc;
    private final CharSequence[] cells;
    private final Layout.Alignment[] alignments;
    private final int columns;
    private final boolean header;
    private final boolean firstRow;

    private StaticLayout[] layouts;
    private int laidWidth = -1;
    private float laidTextSize = -1;
    private int laidColor;
    private int height;

    TableRowSpan(@NonNull RichDoc doc, @NonNull CharSequence[] cells, @NonNull Layout.Alignment[] alignments,
                 int columns, boolean header, boolean firstRow) {
        this.doc = doc;
        this.cells = cells;
        this.alignments = alignments;
        this.columns = Math.max(1, columns);
        this.header = header;
        this.firstRow = firstRow;
    }

    private int padding() {
        return doc.style.px(6);
    }

    private void layout(@NonNull Paint paint, int width) {
        if (layouts != null && width == laidWidth && paint.getTextSize() == laidTextSize
                && paint.getColor() == laidColor) {
            return;
        }
        laidWidth = width;
        laidTextSize = paint.getTextSize();
        laidColor = paint.getColor();
        int column = width / columns;
        int inner = Math.max(1, column - 2 * padding());
        TextPaint cellPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        if (paint instanceof TextPaint) cellPaint.set((TextPaint) paint);
        else cellPaint.set(paint);
        layouts = new StaticLayout[cells.length];
        int tallest = 0;
        for (int i = 0; i < cells.length; i++) {
            CharSequence cell = cells[i];
            if (cell instanceof Spanned) {
                for (ImageSpan image : ((Spanned) cell).getSpans(0, cell.length(), ImageSpan.class)) {
                    image.maxWidth = inner;
                }
            }
            layouts[i] = StaticLayout.Builder.obtain(cell, 0, cell.length(), cellPaint, inner)
                    .setAlignment(alignments[i])
                    .setIncludePad(false)
                    .build();
            tallest = Math.max(tallest, layouts[i].getHeight());
        }
        height = tallest + 2 * padding();
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        int width = Math.max(1, doc.width);
        layout(paint, width);
        if (fm != null) {
            fm.ascent = -height;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return width;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        int width = Math.max(1, doc.width);
        layout(paint, width);
        int column = width / columns;
        // the whole line: rows touch, the lines between them are single
        float rowTop = top;
        float rowBottom = bottom;
        Paint line = new Paint();
        line.setStyle(Paint.Style.FILL);
        if (header) {
            line.setColor(doc.style.codeBackground);
            canvas.drawRect(x, rowTop, x + column * columns, rowBottom, line);
        }
        line.setColor(doc.style.borderColor);
        float stroke = Math.max(1f, doc.style.density);
        if (firstRow) canvas.drawRect(x, rowTop, x + column * columns, rowTop + stroke, line);
        canvas.drawRect(x, rowBottom - stroke, x + column * columns, rowBottom, line);
        for (int i = 0; i <= columns; i++) {
            float lx = x + i * column - (i == columns ? stroke : 0);
            canvas.drawRect(lx, rowTop, lx + stroke, rowBottom, line);
        }
        for (int i = 0; i < layouts.length; i++) {
            canvas.save();
            canvas.translate(x + i * column + padding(), rowTop + padding());
            layouts[i].draw(canvas);
            canvas.restore();
        }
    }
}
