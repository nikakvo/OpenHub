package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;

import org.commonmark.node.BlockQuote;

/**
 * The Markdown renderer of comments and posts (discussions, pull requests, line comments,
 * the editor's preview): GitHub's tables, task lists, strikethrough and links, plus
 * images, both as Markdown {@code ![alt](url)} and as the HTML {@code <img>} tags that
 * GitHub puts in for pasted screenshots. Images are loaded with Glide, no wider than the
 * screen, and scaled down to the width of the text.
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    @NonNull
    public static Markwon create(@NonNull Context context) {
        return create(context, null);
    }

    /**
     * @param onImageLoaded called on the main thread when an image arrived from the network
     *                      (not from the cache), to lay the texts out again with it; see
     *                      {@link #refreshImages}
     */
    @NonNull
    public static Markwon create(@NonNull Context context, @Nullable Runnable onImageLoaded) {
        return build(context, onImageLoaded, false);
    }

    /**
     * For previews in a list that opens the full text on a tap (issue comments): the text
     * gets no link movement method, so it does not take the taps and long-presses of its
     * row, and "maxLines" with an ellipsis still works.
     */
    @NonNull
    public static Markwon createPreview(@NonNull Context context, @Nullable Runnable onImageLoaded) {
        return build(context, onImageLoaded, true);
    }

    @NonNull
    private static Markwon build(@NonNull Context context, @Nullable Runnable onImageLoaded,
                                 boolean preview) {
        final RequestManager glide = Glide.with(context);
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable relayout = onImageLoaded == null ? null : () -> {
            main.removeCallbacksAndMessages(null);
            // several images in a row: one pass after the last one
            main.postDelayed(onImageLoaded, 120);
        };
        final int maxWidth = context.getResources().getDisplayMetrics().widthPixels;
        // pictures that already made the text lay out again once: a picture from the memory
        // cache (opening a screen again) arrives after the layout too, but must not lay the
        // text out again on every bind, or the rebinds would never end
        final java.util.Set<String> arrived =
                java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        final int quoteBar = QuoteStyle.barColor(context);
        final int quoteText = QuoteStyle.textColor(context);
        final int quoteWidth = QuoteStyle.px(context, QuoteStyle.BAR_DP);
        Markwon.Builder builder = Markwon.builder(context);
        if (preview) builder.usePlugin(MovementMethodPlugin.none());
        return builder
                .usePlugin(new AbstractMarkwonPlugin() {
                    // quotes: a 4 dp grey bar and grey text, like QuoteStyle (the default bar
                    // is the text color at 10 %, hard to see)
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.blockQuoteWidth(quoteWidth).blockQuoteColor(quoteBar);
                    }

                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                        builder.appendFactory(BlockQuote.class,
                                (configuration, props) -> new android.text.style.ForegroundColorSpan(quoteText));
                    }
                })
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(new GlideImagesPlugin.GlideStore() {
                    @NonNull
                    @Override
                    public RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable) {
                        // big screenshots are decoded at most as wide as the screen
                        return glide.load(drawable.getDestination())
                                .override(maxWidth, Target.SIZE_ORIGINAL)
                                .fitCenter()
                                .addListener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                                @NonNull Target<Drawable> target, boolean isFirst) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                                   Target<Drawable> target, @NonNull DataSource source,
                                                                   boolean isFirst) {
                                        boolean first = arrived.add(String.valueOf(model));
                                        if (relayout != null && (first || source != DataSource.MEMORY_CACHE)) {
                                            main.post(relayout);
                                        }
                                        return false;
                                    }
                                });
                    }

                    @Override
                    public void cancel(@NonNull Target<?> target) {
                        glide.clear(target);
                    }
                }))
                .build();
    }

    /**
     * Sets every text with images under {@code root} again, so it takes the height of the
     * images that have arrived (a text laid out before its image came from the network kept
     * the empty space of the placeholder).
     */
    public static void refreshImages(@Nullable View root) {
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text instanceof Spanned
                    && ((Spanned) text).getSpans(0, text.length(), AsyncDrawableSpan.class).length > 0) {
                ((TextView) root).setText(text);
            }
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) refreshImages(group.getChildAt(i));
        }
    }
}
