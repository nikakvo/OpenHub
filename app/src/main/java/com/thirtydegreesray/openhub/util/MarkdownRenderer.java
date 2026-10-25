package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.Target;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

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
        final RequestManager glide = Glide.with(context);
        final int maxWidth = context.getResources().getDisplayMetrics().widthPixels;
        return Markwon.builder(context)
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
                                .fitCenter();
                    }

                    @Override
                    public void cancel(@NonNull Target<?> target) {
                        glide.clear(target);
                    }
                }))
                .build();
    }
}
