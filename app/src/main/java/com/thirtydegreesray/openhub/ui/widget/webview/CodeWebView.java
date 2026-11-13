

package com.thirtydegreesray.openhub.ui.widget.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;

import org.json.JSONObject;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

/**
 * Created on 2017/8/20 12:10:56
 * Copied from Copyright (C) 2017 Kosh.
 * Modified by Copyright (C) 2017 ThirtyDegreesRay.
 */

public class CodeWebView extends WebView {

    private ContentChangedListener contentChangedListener;
    private int backgroundColor ;

    public interface ContentChangedListener {
        void onContentChanged(int progress);

        void onScrollChanged(boolean reachedTop, int scroll);
    }

    public CodeWebView(Context context) {
        super(context);
        init(null);
    }

    public CodeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CodeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public CodeWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray tp = getContext().obtainStyledAttributes(attrs, R.styleable.CodeWebView);
            try {
                backgroundColor = tp.getColor(R.styleable.CodeWebView_webview_background,
                        ViewUtils.getWindowBackground(getContext()));
                setBackgroundColor(backgroundColor);
            } finally {
                tp.recycle();
            }
        }

        setWebChromeClient(new ChromeClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setWebViewClient(new WebClientN());
        } else {
            setWebViewClient(new WebClient());
        }
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        // pages from GitHub never need the phone's files, other apps' content or the location
        // (assets under file:///android_asset/ still load)
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setDefaultTextEncodingName("utf-8");
        boolean isLoadImageEnable = PrefUtils.isLoadImageEnable();
        settings.setLoadsImagesAutomatically(isLoadImageEnable);
        settings.setBlockNetworkImage(!isLoadImageEnable);
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult result = getHitTestResult();
                if (hitLinkResult(result) && !StringUtils.isBlank(result.getExtra())) {
                    AppUtils.copyToClipboard(getContext(), result.getExtra());
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * A web view as tall as its page (wrap_content inside a scrolling screen: README, pull
     * request and release text) gets its drawing memory from the part that is on screen.
     * When only a strip of it is visible (a README below the repo info), big animated
     * pictures need more than that and parts of the page vanish for a moment, again and
     * again. Pre-rastering sizes the memory by the whole view and stops the flicker
     * (charmbracelet/vhs: 104 "tile memory limits exceeded" in 10 s before, 0 after,
     * app memory +2 MB).
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && preRasterFor(params.height)) {
            getSettings().setOffscreenPreRaster(true);
        }
    }

    /** True for a web view that takes the height of its page. */
    public static boolean preRasterFor(int layoutHeight) {
        return layoutHeight == ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private float touchDownX, touchDownY;
    private boolean touchDirectionDecided;

    /**
     * Wide tables and code blocks scroll sideways inside the page. Without this the
     * tab pager around (repository tabs) takes every horizontal drag, so they could
     * hardly be moved. A drag that starts mostly horizontal stays with the WebView; a
     * vertical one is handed back so the surrounding scroll view scrolls the page.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchDirectionDecided = false;
                requestParentsDisallowIntercept(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!touchDirectionDecided) {
                    float dx = Math.abs(event.getX() - touchDownX);
                    float dy = Math.abs(event.getY() - touchDownY);
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (dx > slop || dy > slop) {
                        touchDirectionDecided = true;
                        if (dy >= dx) requestParentsDisallowIntercept(false);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentsDisallowIntercept(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    private void requestParentsDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    public void setContentChangedListener(ContentChangedListener contentChangedListener) {
        this.contentChangedListener = contentChangedListener;
    }

    public void setCodeSource(@NonNull String source, boolean wrap) {
        setCodeSource(source, wrap, null);
    }

    public void loadImage(@NonNull String url) {
        WebSettings settings = getSettings();
        // the user tapped this one picture: it loads even when "Disable loading network
        // image" holds pictures back on mobile data (only this page, the setting stays)
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        String html = HtmlHelper.generateImageHtml(url, getCodeBackgroundColor());
        loadHtml(html);
    }

    public void setHtmlSource(@NonNull String htmlSource) {
        WebSettings settings = getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        String html = HtmlHelper.generateHtmlSourceHtml(htmlSource,
                getCodeBackgroundColor(), getAccentColor());
        loadHtml(html);
    }

    public void setMdSource(@NonNull String source, @Nullable String baseUrl) {
        setMdSource(source, baseUrl, false);
    }

    public void setMdSource(@NonNull String source, @Nullable String baseUrl, boolean wrapCode) {
        if (StringUtils.isBlank(source)) return;
        String page = HtmlHelper.generateMdHtml(source, baseUrl, AppUtils.isNightMode(),
                getCodeBackgroundColor(), getAccentColor(), wrapCode);
        loadPage(page);
    }

    public void setCodeSource(@NonNull String source, boolean wrap, @Nullable String extension) {
        if (StringUtils.isBlank(source)) return;
        WebSettings settings = getSettings();
        // the page has the phone's width (viewport meta) and the code its real font size:
        // no fit-to-width zoom and no text boosting, so wrapped and unwrapped lines have the
        // same size and only the line breaks differ
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        String page = HtmlHelper.generateCodeHtml(source, extension, AppUtils.isNightMode(),
                getCodeBackgroundColor(), wrap, true);
        loadPage(page);
    }

    public void setDiffFileSource(@NonNull String source, boolean wrap) {
        if (StringUtils.isBlank(source)) return;
        WebSettings settings = getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        String page = HtmlHelper.generateDiffHtml(source, AppUtils.isNightMode(),
                getCodeBackgroundColor(), wrap);
        loadPage(page);
    }

    private void loadPageWithBaseUrl(final String baseUrl, final String page){
        post(new Runnable() {
            @Override
            public void run() {
                loadDataWithBaseURL(baseUrl, page, "text/html", "utf-8", null);
            }
        });
    }

    private void loadHtml(String html) {
        loadPageWithBaseUrl("file:///android_asset/", html);
    }

    private void loadPage(String page) {
        loadPageWithBaseUrl("file:///android_asset/code_prettify/", page);
    }

    private boolean hitLinkResult(WebView.HitTestResult result) {
        return result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                result.getType() == HitTestResult.IMAGE_TYPE ||
                result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
    }

    private class ChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            super.onProgressChanged(view, progress);
            if (contentChangedListener != null) {
                contentChangedListener.onContentChanged(progress);
            }
        }
    }

    private class WebClientN extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            startActivity(request.getUrl());
            return true;
        }
    }

    private class WebClient extends WebViewClient {
        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            startActivity(Uri.parse(url));
            return true;
        }
    }

    private String getCodeBackgroundColor(){
        return "#" + Integer.toHexString(backgroundColor).substring(2).toUpperCase();
    }
    private String getAccentColor(){
        return "#" + Integer.toHexString(ViewUtils.getAccentColor(getContext())).substring(2).toUpperCase();
    }

    private void startActivity(Uri uri){
        if(uri == null) return;
        // the page's own address (file:///android_asset/...): "#anchor" links of the page
        if (HtmlHelper.ANCHOR_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            scrollToAnchor(uri.getSchemeSpecificPart());
            return;
        }
        // a copy button of a code block: the code goes to the clipboard
        if (HtmlHelper.COPY_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            String code = Uri.decode(uri.getEncodedSchemeSpecificPart());
            if (code != null) com.thirtydegreesray.openhub.util.AppUtils.copyToClipboard(getContext(), code);
            return;
        }
        // a video of the page started: the app's player, with the page's own source
        if (HtmlHelper.VIDEO_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            String source = Uri.decode(uri.getEncodedSchemeSpecificPart());
            if (source != null && source.startsWith("https://")) {
                com.thirtydegreesray.openhub.ui.activity.VideoPlayerActivity.show(getContext(), source);
            }
            return;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            scrollToAnchor(uri.getFragment());
            return;
        }
        AppOpener.launchUrl(getContext(), uri);
    }

    /**
     * Table-of-contents links ("#installation"). GitHub renders the target as
     * id="user-content-installation"; the browser cannot find it by "#installation".
     */
    private void scrollToAnchor(@Nullable String anchor) {
        if (StringUtils.isBlank(anchor)) return;
        String js = "(function(n){"
                + "var e=document.getElementById('user-content-'+n)||document.getElementById(n)"
                + "||document.getElementsByName('user-content-'+n)[0]||document.getElementsByName(n)[0];"
                + "if(!e){var l=n.toLowerCase();"
                + "e=document.getElementById('user-content-'+l)||document.getElementById(l);}"
                + "if(!e)return -1;"
                + "return e.getBoundingClientRect().top+window.scrollY;"
                + "})(" + JSONObject.quote(anchor) + ")";
        evaluateJavascript(js, value -> {
            float y;
            try {
                y = Float.parseFloat(value);
            } catch (Exception e) {
                return;
            }
            if (y < 0) return;
            scrollToContentY(Math.round(y * getResources().getDisplayMetrics().density));
        });
    }

    /** Scrolls the page itself, or the scroll view around it when it shows the whole page. */
    private void scrollToContentY(int y) {
        if (canScrollVertically(1) || canScrollVertically(-1)) {
            scrollTo(getScrollX(), y);
            return;
        }
        int top = getTop();
        ViewParent parent = getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view instanceof NestedScrollView) {
                ((NestedScrollView) view).smoothScrollTo(0, top + y);
                return;
            }
            if (view instanceof ScrollView) {
                ((ScrollView) view).smoothScrollTo(0, top + y);
                return;
            }
            top += view.getTop();
            parent = view.getParent();
        }
    }
}

