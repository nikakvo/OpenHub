package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IGistsContract;
import com.thirtydegreesray.openhub.mvp.model.Gist;
import com.thirtydegreesray.openhub.mvp.model.GistFile;
import com.thirtydegreesray.openhub.mvp.presenter.GistsPresenter;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;
import com.thirtydegreesray.openhub.ui.adapter.GistsAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GitHubHelper;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Gists of a user. Tap: its files (tap a file to view it, the arrow downloads it),
 * long tap: open the gist on GitHub.
 */
public class GistsFragment extends ListFragment<GistsPresenter, GistsAdapter>
        implements IGistsContract.View {

    /** Bigger files are opened in the browser; intents cannot carry them. */
    private static final int MAX_INLINE_CHARS = 200_000;

    public static GistsFragment create(@NonNull String user) {
        GistsFragment fragment = new GistsFragment();
        fragment.setArguments(BundleHelper.builder().put("user", user).build());
        return fragment;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_list;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {
        DaggerFragmentComponent.builder()
                .appComponent(appComponent)
                .fragmentModule(new FragmentModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(true);
    }

    @Override
    public void showGists(ArrayList<Gist> gists) {
        adapter.setData(gists);
        postNotifyDataSetChanged();
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadGists(1, true);
    }

    @Override
    protected void onLoadMore(int page) {
        super.onLoadMore(page);
        mPresenter.loadGists(page, false);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.no_gists);
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        mPresenter.openGist(adapter.getData().get(position));
    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        AppOpener.openInCustomTabsOrBrowser(getActivity(), adapter.getData().get(position).getHtmlUrl());
        return true;
    }

    @Override
    public void showGistFiles(final Gist gist) {
        if (getActivity() == null) return;
        final List<GistFile> files = gist.getFileList();
        new AlertDialog.Builder(getActivity())
                .setTitle(gist.getTitle())
                .setAdapter(new FileListAdapter(files), (dialog, which) -> openFile(files.get(which)))
                .setNeutralButton(R.string.open_in_browser, (dialog, which) ->
                        AppOpener.openInCustomTabsOrBrowser(getActivity(), gist.getHtmlUrl()))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void openFile(GistFile file) {
        String content = file.getContent();
        if (content == null || file.isTruncated() || content.length() > MAX_INLINE_CHARS) {
            AppOpener.openInCustomTabsOrBrowser(getActivity(), file.getRawUrl());
        } else if (GitHubHelper.isMarkdown(file.getFilename())) {
            ViewerActivity.showMdSource(getActivity(), file.getFilename(), MarkdownHtml.render(content));
        } else {
            ViewerActivity.showCode(getActivity(), file.getFilename(), content);
        }
    }

    private void downloadFile(GistFile file) {
        AppOpener.startDownload(getActivity(), file.getRawUrl(), file.getFilename());
    }

    /** File rows: name, language and size, and a download arrow. */
    private class FileListAdapter extends BaseAdapter {
        private final List<GistFile> files;

        FileListAdapter(List<GistFile> files) {
            this.files = files;
        }

        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public Object getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView : LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_item_artifact, parent, false);
            final GistFile file = files.get(position);
            ((TextView) row.findViewById(R.id.artifact_name)).setText(file.getFilename());
            String meta = StringUtils.getSizeString(file.getSize());
            if (!StringUtils.isBlank(file.getLanguage())) meta = file.getLanguage() + " · " + meta;
            ((TextView) row.findViewById(R.id.artifact_size)).setText(meta);
            ImageView download = row.findViewById(R.id.artifact_download);
            download.setOnClickListener(v -> downloadFile(file));
            return row;
        }
    }

}
