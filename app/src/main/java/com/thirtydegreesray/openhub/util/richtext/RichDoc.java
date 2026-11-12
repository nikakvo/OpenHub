package com.thirtydegreesray.openhub.util.richtext;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.thirtydegreesray.openhub.R;

import java.lang.ref.WeakReference;
import org.jsoup.Jsoup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A rendered text with its pictures. It can be kept and shown again (a list row bound
 * again): pictures that have arrived stay, so the text does not jump. While it is shown
 * in a text view, an arriving picture lays that text out again.
 */
public final class RichDoc {

    final RichStyle style;
    final SpannableStringBuilder text = new SpannableStringBuilder();
    final List<ImageSpan> images = new ArrayList<>();
    boolean hasTables;
    /** width of the text in pixels (pictures and tables are fitted into it) */
    int width;
    /**
     * The HTML, kept only when the text has {@code <details>}: opening or closing one
     * renders the text again from it.
     */
    @Nullable
    private String source;
    @NonNull
    private String playVideo = "";
    /** the {@code <details>} the user opened (true) or closed (false), by their number in the text */
    private final Map<Integer, Boolean> detailsOpen = new HashMap<>();
    /** pictures of the previous rendering, used again by the next one (no new load, no jump) */
    @Nullable
    private Map<String, ArrayDeque<ImageSpan>> oldImages;

    private WeakReference<TextView> host = new WeakReference<>(null);
    private final Runnable relayout = this::relayoutNow;

    private final Drawable.Callback animation = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            TextView view = shownIn();
            if (view != null) view.invalidate();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            TextView view = shownIn();
            if (view != null) view.postDelayed(what, when - SystemClock.uptimeMillis());
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            TextView view = shownIn();
            if (view != null) view.removeCallbacks(what);
        }
    };

    RichDoc(@NonNull RichStyle style) {
        this.style = style;
    }

    @NonNull
    public CharSequence getText() {
        return text;
    }

    /** Renders {@code html} into the text; {@code html} is kept when it has {@code <details>}. */
    void build(@NonNull String html, @NonNull String playVideo) {
        this.source = html;
        this.playVideo = playVideo;
        rebuild();
        if (detailsIds.isEmpty()) source = null;
    }

    /** the {@code <details>} of the last rendering: their number in the HTML */
    @NonNull
    private Map<org.jsoup.nodes.Element, Integer> detailsIds = new java.util.IdentityHashMap<>();

    private void rebuild() {
        String html = source;
        if (html == null) return;
        oldImages = new HashMap<>();
        for (ImageSpan span : images) {
            String key = String.valueOf(span.source);
            ArrayDeque<ImageSpan> same = oldImages.get(key);
            if (same == null) oldImages.put(key, same = new ArrayDeque<>());
            same.add(span);
        }
        images.clear();
        text.clearSpans();
        text.clear();
        hasTables = false;
        org.jsoup.nodes.Element body = Jsoup.parseBodyFragment(html).body();
        detailsIds = new java.util.IdentityHashMap<>();
        int number = 0;
        for (org.jsoup.nodes.Element details : body.select("details")) detailsIds.put(details, number++);
        new HtmlSpanner(this, text, playVideo, detailsIds).render(body);
        oldImages = null;
    }

    /** A picture of the previous rendering with the same source, or null. */
    @Nullable
    ImageSpan reuseImage(@NonNull Object source) {
        if (oldImages == null) return null;
        ArrayDeque<ImageSpan> same = oldImages.get(String.valueOf(source));
        return same == null ? null : same.poll();
    }

    /** Whether {@code <details>} number {@code id} is open: the user's choice, else its {@code open} attribute. */
    boolean isOpen(int id, boolean byDefault) {
        Boolean chosen = detailsOpen.get(id);
        return chosen != null ? chosen : byDefault;
    }

    /** Opens or closes {@code <details>} number {@code id} and shows the new text in {@code view}. */
    void toggle(int id, boolean wasOpen, @NonNull TextView view) {
        if (source == null) return;
        detailsOpen.put(id, !wasOpen);
        rebuild();
        if (view.getTag(R.id.rich_text_doc) == this) {
            view.setText(text, TextView.BufferType.SPANNABLE);
            startLoads(view);
        }
    }

    void attach(@NonNull TextView view) {
        host = new WeakReference<>(view);
    }

    /** The text view that shows this text now, or null. */
    @Nullable
    TextView shownIn() {
        TextView view = host.get();
        return view != null && view.getTag(R.id.rich_text_doc) == this ? view : null;
    }

    boolean dependsOnWidth() {
        return hasTables || !images.isEmpty();
    }

    void startLoads(@NonNull TextView view) {
        if (images.isEmpty() || !style.loadImages) return;
        Activity activity = activityOf(view.getContext());
        if (activity != null && (activity.isDestroyed() || activity.isFinishing())) return;
        RequestManager glide = null;
        int max = Math.max(view.getResources().getDisplayMetrics().widthPixels, 1);
        for (final ImageSpan span : images) {
            // every picture is loaded once; pictures of an opened <details> come later
            if (span.requested) continue;
            span.requested = true;
            if (glide == null) glide = Glide.with(view.getContext());
            glide.load(span.source)
                    // big screenshots are decoded at most as wide as the screen, small
                    // pictures stay as they are (never scaled up)
                    .override(max, Target.SIZE_ORIGINAL)
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .dontTransform()
                    .placeholder((Drawable) null)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource,
                                                    @Nullable Transition<? super Drawable> transition) {
                            span.drawable = resource;
                            if (resource instanceof Animatable) {
                                resource.setCallback(animation);
                                ((Animatable) resource).start();
                            }
                            requestRelayout();
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            Drawable old = span.drawable;
                            if (old instanceof Animatable) ((Animatable) old).stop();
                            span.drawable = null;
                        }
                    });
        }
    }

    /** One layout pass after several pictures arrived close together. */
    void requestRelayout() {
        TextView view = shownIn();
        if (view == null) return;
        view.removeCallbacks(relayout);
        view.postDelayed(relayout, 80);
    }

    private void relayoutNow() {
        TextView view = shownIn();
        if (view != null) view.setText(text, TextView.BufferType.SPANNABLE);
    }

    @Nullable
    private static Activity activityOf(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
