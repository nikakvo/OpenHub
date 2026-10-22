package com.thirtydegreesray.openhub.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUpdater;
import com.thirtydegreesray.openhub.util.MatrixScreen;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The in-app help: assets/help.html in the colors of the chosen theme and accent.
 * Works offline; links to github.com open in the app, other links in the browser.
 */
public class HelpActivity extends BaseActivity<IBaseContract.Presenter> {

    public static void show(@NonNull Context context) {
        context.startActivity(new Intent(context, HelpActivity.class));
    }

    private WebView web;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_help;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.help));
        ViewGroup root = findViewById(R.id.help_root);
        if (root != null) {
            MatrixScreen.apply(this, root, getString(R.string.matrix_help_title),
                    getString(R.string.matrix_help_subtitle, AppUpdater.currentVersion()));
        }
        String html;
        try {
            html = read("help.html");
        } catch (IOException e) {
            finish();
            return;
        }
        String[] palette = palette();
        html = html.replace("{{THEME_VARS}}", ":root { " + palette[0] + " }")
                .replace("{{VERSION}}", AppUpdater.currentVersion())
                .replace("{{MATRIX}}", MatrixScreen.isDark(this) ? "matrix" : "");
        web.setBackgroundColor(Color.parseColor(palette[1]));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    AppOpener.launchUrl(HelpActivity.this, uri);
                    return true;
                }
                return false;
            }
        });
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** CSS variables for the current theme, and the page background. */
    @NonNull
    private String[] palette() {
        String accent = String.format(Locale.ROOT, "#%06X", 0xFFFFFF & ViewUtils.getAccentColor(this));
        String theme = PrefUtils.getTheme();
        String bg;
        String vars;
        if (MatrixScreen.isDark(this)) {
            // the almost black of the side menu, cards with a faint green tint
            bg = "#070B08";
            vars = "--bg:#070B08; --surface:#111713; --surface2:#0B100D; --border:#1E2A22; --border2:#2A3A2F;"
                    + " --text:#D7DCE1; --dim:#8A9690; --hi:#FFFFFF; --matrix:#39FF7A;";
        } else if (PrefUtils.AMOLED_DARK.equals(theme)) {
            bg = "#000000";
            vars = "--bg:#000000; --surface:#0D0F12; --surface2:#08090B; --border:#22262C; --border2:#30353C;"
                    + " --text:#D7DCE1; --dim:#8B949E; --hi:#FFFFFF;";
        } else if (PrefUtils.DARK.equals(theme)) {
            bg = "#22252A";
            vars = "--bg:#22252A; --surface:#2D3035; --surface2:#26282C; --border:#3A3F46; --border2:#4A5058;"
                    + " --text:#D7DCE1; --dim:#8B949E; --hi:#FFFFFF;";
        } else {
            bg = "#F6F8FA";
            vars = "--bg:#F6F8FA; --surface:#FFFFFF; --surface2:#F3F4F6; --border:#E1E4E8; --border2:#D0D7DE;"
                    + " --text:#24292F; --dim:#57606A; --hi:#000000;"
                    + " --green:#1A7F37; --purple:#8250DF; --red:#CF222E; --amber:#9A6700; --grey:#6E7781;"
                    + " --blue:#0969DA;";
        }
        return new String[]{vars + " --acc:" + accent + ";", bg};
    }

    @NonNull
    private String read(@NonNull String asset) throws IOException {
        try (InputStream in = getAssets().open(asset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        web = root.findViewById(R.id.help_web);
    }
}
