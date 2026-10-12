package com.thirtydegreesray.openhub.ui.widget;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.MarkdownService;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;

/**
 * Write / Preview tabs for a Markdown field (layout_markdown_write_preview), like the
 * editors on github.com. The preview is rendered by GitHub itself, with the
 * repository as context so #123, @user and commit hashes become links; without a
 * connection it falls back to the built-in renderer.
 */
public class MarkdownWritePreview {

    private final View tabWrite;
    private final View tabPreview;
    private final TextView tabWriteText;
    private final TextView tabPreviewText;
    private final View tabWriteLine;
    private final View tabPreviewLine;
    private final TextInputLayout inputLayout;
    private final TextInputEditText edit;
    private final View previewContainer;
    private final CodeWebView previewWeb;
    private final TextView previewEmpty;
    private final View previewLoader;
    @Nullable private final String repoFullName;
    /** text color of an unselected tab (from the style) */
    private final ColorStateList normalColor;

    private boolean preview;
    private String renderedText;
    private Disposable rendering;

    /**
     * @param root         a view containing layout_markdown_write_preview
     * @param repoFullName "owner/repo" for links, or null
     */
    public MarkdownWritePreview(@NonNull View root, @Nullable String repoFullName) {
        this.repoFullName = repoFullName;
        tabWrite = root.findViewById(R.id.md_tab_write);
        tabPreview = root.findViewById(R.id.md_tab_preview);
        tabWriteText = root.findViewById(R.id.md_tab_write_text);
        tabPreviewText = root.findViewById(R.id.md_tab_preview_text);
        tabWriteLine = root.findViewById(R.id.md_tab_write_line);
        tabPreviewLine = root.findViewById(R.id.md_tab_preview_line);
        inputLayout = root.findViewById(R.id.md_input_layout);
        edit = root.findViewById(R.id.md_edit);
        previewContainer = root.findViewById(R.id.md_preview_container);
        previewWeb = root.findViewById(R.id.md_preview_web);
        previewEmpty = root.findViewById(R.id.md_preview_empty);
        previewLoader = root.findViewById(R.id.md_preview_loader);
        normalColor = tabWriteText.getTextColors();
        tabWrite.setOnClickListener(v -> showWrite());
        tabPreview.setOnClickListener(v -> showPreview());
        updateTabs();
    }

    @NonNull
    public TextInputEditText getEditText() {
        return edit;
    }

    public void setHint(@NonNull CharSequence hint) {
        inputLayout.setHint(hint);
    }

    @NonNull
    public String getText() {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    public boolean isPreview() {
        return preview;
    }

    public void showWrite() {
        if (!preview) return;
        preview = false;
        inputLayout.setVisibility(View.VISIBLE);
        previewContainer.setVisibility(View.GONE);
        updateTabs();
        edit.requestFocus();
    }

    public void showPreview() {
        if (preview) return;
        preview = true;
        AppUtils.hideKeyboard(edit);
        inputLayout.setVisibility(View.GONE);
        previewContainer.setVisibility(View.VISIBLE);
        updateTabs();
        render();
    }

    public void dispose() {
        if (rendering != null) rendering.dispose();
    }

    private void updateTabs() {
        ColorStateList accent = ColorStateList.valueOf(ViewUtils.getAccentColor(tabWrite.getContext()));
        tabWriteText.setTextColor(preview ? normalColor : accent);
        tabPreviewText.setTextColor(preview ? accent : normalColor);
        tabWriteText.setTypeface(null, preview ? Typeface.NORMAL : Typeface.BOLD);
        tabPreviewText.setTypeface(null, preview ? Typeface.BOLD : Typeface.NORMAL);
        tabWriteLine.setVisibility(preview ? View.INVISIBLE : View.VISIBLE);
        tabPreviewLine.setVisibility(preview ? View.VISIBLE : View.INVISIBLE);
    }

    private void render() {
        final String text = getText();
        if (text.trim().isEmpty()) {
            renderedText = null;
            previewWeb.setVisibility(View.GONE);
            previewEmpty.setVisibility(View.VISIBLE);
            return;
        }
        previewEmpty.setVisibility(View.GONE);
        previewWeb.setVisibility(View.VISIBLE);
        if (text.equals(renderedText)) return;
        if (rendering != null) rendering.dispose();
        previewLoader.setVisibility(View.VISIBLE);
        rendering = Single.fromCallable(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("mode", "gfm");
            if (repoFullName != null) body.put("context", repoFullName);
            ResponseBody html = GitHubApi.call(GitHubApi.service(MarkdownService.class).render(body));
            return "<div class=\"markdown-body\">" + (html == null ? "" : html.string()) + "</div>";
        }).onErrorReturn(error -> MarkdownHtml.render(text))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(html -> {
                    previewLoader.setVisibility(View.GONE);
                    renderedText = text;
                    previewWeb.setMdSource(html, null);
                });
    }
}
