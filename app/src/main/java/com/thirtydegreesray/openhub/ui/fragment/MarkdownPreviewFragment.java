package com.thirtydegreesray.openhub.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.ui.activity.MarkdownEditorCallback;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.GitHubMarkdown;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.StringUtils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The Preview tab of the Markdown editor: the text as GitHub renders it (POST /markdown,
 * with the repository as context when the editor has one); without a connection the
 * phone renders it.
 *
 * Created by ThirtyDegreesRay on 2017/9/29 11:52:42
 */
public class MarkdownPreviewFragment extends BaseFragment {

    public static MarkdownPreviewFragment create() {
        return new MarkdownPreviewFragment();
    }

    private CodeWebView previewWeb;
    private TextView previewText;
    private View previewLoader;
    @Nullable private String renderedText;
    @Nullable private Disposable rendering;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_markdown_preview;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {

    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        previewText.setText(R.string.nothing_to_preview);
    }

    private MarkdownEditorCallback getMarkdownEditorCallback() {
        return (MarkdownEditorCallback) getActivity();
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        final String text = getMarkdownEditorCallback().getText();
        if (StringUtils.isBlank(text)) {
            renderedText = null;
            previewWeb.setVisibility(View.GONE);
            previewText.setVisibility(View.VISIBLE);
            return;
        }
        previewText.setVisibility(View.GONE);
        previewWeb.setVisibility(View.VISIBLE);
        if (text.equals(renderedText)) return;
        if (rendering != null) rendering.dispose();
        previewLoader.setVisibility(View.VISIBLE);
        final Context context = requireContext().getApplicationContext();
        final String repo = getMarkdownEditorCallback().getRepoFullName();
        rendering = Single.fromCallable(() -> GitHubMarkdown.preview(context, text, repo))
                .onErrorReturn(error -> MarkdownHtml.renderComment(text, repo))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(html -> {
                    previewLoader.setVisibility(View.GONE);
                    renderedText = text;
                    previewWeb.setMdSource(html, null);
                });
    }

    @Override
    public void onDestroyView() {
        if (rendering != null) rendering.dispose();
        super.onDestroyView();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        previewWeb = root.findViewById(R.id.preview_web);
        previewText = root.findViewById(R.id.preview_text);
        previewLoader = root.findViewById(R.id.preview_loader);
    }
}
