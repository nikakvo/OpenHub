package com.thirtydegreesray.openhub.util.richtext;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.resource.gif.GifDrawable;

/**
 * A picture in the text. Until it has arrived it takes the size given in the HTML
 * ({@code width} / {@code height}, as on github.com) or no space. A picture is shown at the
 * size it has on github.com (one CSS pixel = one dp), never wider than the text.
 */
final class ImageSpan extends ReplacementSpan {

    private final RichDoc doc;
    /** what Glide loads: a link, or the file on the phone (content://) of a fresh upload */
    final Object source;
    private final int attrWidth;
    private final int attrHeight;
    /** GitHub's own emoji pictures (:octocat:): as high as the text */
    private final boolean emoji;
    @Nullable Drawable drawable;
    /** a narrower limit inside a table cell; 0 = the width of the text */
    int maxWidth;

    ImageSpan(@NonNull RichDoc doc, @NonNull Object source, int attrWidth, int attrHeight, boolean emoji) {
        this.doc = doc;
        this.source = source;
        this.attrWidth = attrWidth;
        this.attrHeight = attrHeight;
        this.emoji = emoji;
    }

    /** {width, height} in pixels for the text size of {@code paint}. */
    @NonNull
    private int[] size(@NonNull Paint paint) {
        if (emoji) {
            int s = Math.round(paint.getTextSize() * 1.2f);
            return new int[]{s, s};
        }
        float d = doc.style.density;
        int max = maxWidth > 0 ? maxWidth : doc.width;
        float w;
        float h;
        Drawable image = drawable;
        if (image != null) {
            int iw = Math.max(1, pixelWidth(image));
            int ih = Math.max(1, pixelHeight(image));
            if (attrWidth > 0) {
                w = attrWidth * d;
                h = attrHeight > 0 ? attrHeight * d : w * ih / iw;
            } else if (attrHeight > 0) {
                h = attrHeight * d;
                w = h * iw / ih;
            } else {
                w = iw * d;
                h = ih * d;
            }
        } else if (attrWidth > 0 && attrHeight > 0) {
            w = attrWidth * d;
            h = attrHeight * d;
        } else {
            return new int[]{0, 0};
        }
        if (max > 0 && w > max) {
            h = h * max / w;
            w = max;
        }
        return new int[]{Math.max(1, Math.round(w)), Math.max(1, Math.round(h))};
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        int[] s = size(paint);
        if (fm != null && !emoji && s[1] > 0) {
            fm.ascent = -s[1];
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return s[0];
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        Drawable image = drawable;
        if (image == null) return;
        int[] s = size(paint);
        canvas.save();
        float transY = emoji ? y - s[1] + paint.getFontMetrics().descent : bottom - s[1];
        canvas.translate(x, transY);
        image.setBounds(0, 0, s[0], s[1]);
        image.draw(canvas);
        canvas.restore();
    }

    private static int pixelWidth(@NonNull Drawable d) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap().getWidth();
        }
        if (d instanceof GifDrawable && ((GifDrawable) d).getFirstFrame() != null) {
            return ((GifDrawable) d).getFirstFrame().getWidth();
        }
        return d.getIntrinsicWidth();
    }

    private static int pixelHeight(@NonNull Drawable d) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap().getHeight();
        }
        if (d instanceof GifDrawable && ((GifDrawable) d).getFirstFrame() != null) {
            return ((GifDrawable) d).getFirstFrame().getHeight();
        }
        return d.getIntrinsicHeight();
    }
}
