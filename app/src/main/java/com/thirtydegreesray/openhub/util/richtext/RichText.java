package com.thirtydegreesray.openhub.util.richtext;

import android.content.Context;
import android.annotation.SuppressLint;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.StringUtils;


/**
 * Comments, posts and notes in a text view, the way github.com shows them. The text is
 * GitHub's own rendering (the {@code body_html} of the REST API, {@code bodyHTML} of
 * GraphQL); when there is none (a draft on the phone), the Markdown is rendered on the
 * phone. Pictures load with Glide and the text takes their height when they arrive.
 */
public final class RichText {

    private RichText() {
    }

    /** Renders once; the result can be kept and shown again with {@link #show(TextView, RichDoc, boolean)}. */
    @NonNull
    public static RichDoc render(@NonNull Context context, @Nullable String html, @Nullable String markdown,
                                 boolean clickable) {
        RichDoc doc = new RichDoc(new RichStyle(context, clickable));
        String source = !StringUtils.isBlank(html) ? html
                : !StringUtils.isBlank(markdown) ? MarkdownHtml.renderComment(markdown) : "";
        if (source.isEmpty()) return doc;
        doc.build(source, context.getString(R.string.rich_text_play_video));
        return doc;
    }

    /** A whole text with working links, pictures and videos (comments in a conversation). */
    public static void show(@NonNull TextView view, @Nullable String html, @Nullable String markdown) {
        show(view, render(view.getContext(), html, markdown, true), true);
    }

    /**
     * Shows a rendered text. {@code links}: the text takes taps on links; without it the
     * taps and long-presses go to the row around it (lists that open the item on a tap).
     */
    public static void show(@NonNull TextView view, @NonNull RichDoc doc, boolean links) {
        view.setTag(R.id.rich_text_doc, doc);
        doc.attach(view);
        doc.width = contentWidth(view);
        if (links) view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setOnTouchListener(COPY_TOUCH);
        view.setText(doc.text, TextView.BufferType.SPANNABLE);
        doc.startLoads(view);
        if (doc.dependsOnWidth()) watchWidth(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    private static final View.OnTouchListener COPY_TOUCH = (v, event) -> onCopyTouch((TextView) v, event);

    /**
     * A tap on the copy mark of a code block copies the code; a tap on the summary line of
     * a {@code <details>} opens or closes it. Views that need their own touch listener call
     * this first and stop when it returns true (the tap then does not open the comment).
     */
    public static boolean onCopyTouch(@NonNull TextView view, @NonNull MotionEvent event) {
        CharSequence text = view.getText();
        if (!(text instanceof Spanned) || view.getLayout() == null) return false;
        Spanned spanned = (Spanned) text;
        int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        int slop = Math.round(12 * view.getResources().getDisplayMetrics().density);
        for (Spans.CodeBlock block : spanned.getSpans(0, spanned.length(), Spans.CodeBlock.class)) {
            if (!block.hitsMark(x, y, slop)) continue;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                String code = spanned.subSequence(spanned.getSpanStart(block), spanned.getSpanEnd(block))
                        .toString().replace("\u200B", "");
                AppUtils.copyToClipboard(view.getContext(), code);
            }
            return true;
        }
        return onDetailsTouch(view, spanned, x, y, event);
    }

    private static boolean onDetailsTouch(@NonNull TextView view, @NonNull Spanned spanned, int x, int y,
                                          @NonNull MotionEvent event) {
        Object tag = view.getTag(R.id.rich_text_doc);
        if (!(tag instanceof RichDoc)) return false;
        android.text.Layout layout = view.getLayout();
        if (y < 0 || y > layout.getHeight() || x < 0) return false;
        int line = layout.getLineForVertical(y);
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);
        for (Spans.Toggle toggle : spanned.getSpans(lineStart, lineEnd, Spans.Toggle.class)) {
            if (spanned.getSpanStart(toggle) >= lineEnd || spanned.getSpanEnd(toggle) <= lineStart) continue;
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
                ((RichDoc) tag).toggle(toggle.id, toggle.open, view);
            }
            return true;
        }
        return false;
    }

    /** The width the text can use; before the first layout an estimate from the screen. */
    private static int contentWidth(@NonNull TextView view) {
        int width = view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
        if (width > 0) return width;
        int screen = view.getResources().getDisplayMetrics().widthPixels;
        float density = view.getResources().getDisplayMetrics().density;
        return Math.max(1, screen - Math.round(48 * density));
    }

    /** Pictures and tables are fitted to the real width once the text view has it. */
    private static void watchWidth(@NonNull TextView view) {
        if (view.getTag(R.id.rich_text_width) != null) return;
        View.OnLayoutChangeListener listener = (v, l, t, r, b, ol, ot, or, ob) -> {
            Object tag = v.getTag(R.id.rich_text_doc);
            if (!(tag instanceof RichDoc)) return;
            RichDoc doc = (RichDoc) tag;
            if (!doc.dependsOnWidth()) return;
            int width = contentWidth((TextView) v);
            if (width != doc.width && (r - l) > 0) {
                doc.width = width;
                doc.requestRelayout();
            }
        };
        view.setTag(R.id.rich_text_width, listener);
        view.addOnLayoutChangeListener(listener);
    }
}
