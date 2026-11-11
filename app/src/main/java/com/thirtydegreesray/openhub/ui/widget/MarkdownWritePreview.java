package com.thirtydegreesray.openhub.ui.widget;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.GitHubMarkdown;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.ViewUtils;


import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Write / Preview tabs for a Markdown field (layout_markdown_write_preview), like the
 * editors on github.com. The preview is rendered by GitHub itself, with the
 * repository as context so #123, @user and commit hashes become links; without a
 * connection it falls back to the built-in renderer.
 */
public class MarkdownWritePreview {

    private final View tabsRow;
    private final View tabsDivider;
    private final TextView tabWrite;
    private final TextView tabPreview;
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
        tabsRow = root.findViewById(R.id.md_tabs);
        tabsDivider = root.findViewById(R.id.md_tabs_divider);
        tabWrite = root.findViewById(R.id.md_tab_write);
        tabPreview = root.findViewById(R.id.md_tab_preview);
        inputLayout = root.findViewById(R.id.md_input_layout);
        edit = root.findViewById(R.id.md_edit);
        previewContainer = root.findViewById(R.id.md_preview_container);
        previewWeb = root.findViewById(R.id.md_preview_web);
        previewEmpty = root.findViewById(R.id.md_preview_empty);
        previewLoader = root.findViewById(R.id.md_preview_loader);
        normalColor = tabWrite.getTextColors();
        tabWrite.setOnClickListener(v -> showWrite());
        tabPreview.setOnClickListener(v -> showPreview());
        updateTabs();
    }

    /** Shows or hides the tabs (a file that is not Markdown has nothing to preview). */
    public void setPreviewAvailable(boolean available) {
        if (!available) showWrite();
        tabsRow.setVisibility(available ? View.VISIBLE : View.GONE);
        tabsDivider.setVisibility(available ? View.VISIBLE : View.GONE);
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

    /**
     * Shows the paperclip in the tabs row: it uploads a picture or a video with
     * {@code flow} and puts its link at the cursor.
     */
    public void enableAttach(@NonNull AttachFlow flow) {
        View attach = tabsRow.findViewById(R.id.md_attach);
        if (attach == null || !flow.isAvailable()) return;
        attach.setVisibility(View.VISIBLE);
        attach.setOnClickListener(v -> {
            flow.setHost(markdown -> {
                showWrite();
                Attachments.Insertion insertion = Attachments.insert(getText(),
                        edit.getSelectionStart(), edit.getSelectionEnd(), markdown);
                edit.setText(insertion.text);
                edit.setSelection(Math.min(insertion.cursor, edit.length()));
                edit.requestFocus();
            });
            flow.start();
        });
    }

    public void dispose() {
        if (rendering != null) rendering.dispose();
    }

    private void updateTabs() {
        ColorStateList accent = ColorStateList.valueOf(ViewUtils.getAccentColor(tabWrite.getContext()));
        style(tabWrite, !preview, accent);
        style(tabPreview, preview, accent);
    }

    private void style(@NonNull TextView tab, boolean selected, @NonNull ColorStateList accent) {
        tab.setTextColor(selected ? accent : normalColor);
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected);
        } else {
            TypedValue ripple = new TypedValue();
            tab.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true);
            tab.setBackgroundResource(ripple.resourceId);
        }
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
        final android.content.Context context = edit.getContext().getApplicationContext();
        rendering = Single.fromCallable(() -> GitHubMarkdown.preview(context, text, repoFullName))
                .onErrorReturn(error -> MarkdownHtml.renderComment(text))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(html -> {
                    previewLoader.setVisibility(View.GONE);
                    renderedText = text;
                    previewWeb.setMdSource(html, null);
                });
    }
}
