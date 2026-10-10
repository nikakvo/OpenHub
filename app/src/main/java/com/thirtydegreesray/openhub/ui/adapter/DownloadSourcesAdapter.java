package com.thirtydegreesray.openhub.ui.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import android.view.View;
import android.widget.TextView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.DownloadSource;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;


/**
 * Created by ThirtyDegreesRay on 2017/9/16 15:53:46
 */

public class DownloadSourcesAdapter extends BaseAdapter<DownloadSourcesAdapter.ViewHolder, DownloadSource> {

    private final String repoName;
    private final String tagName;

    public DownloadSourcesAdapter(Context context, String repoName, String tagName) {
        super(context);
        this.repoName = repoName;
        this.tagName = tagName;
    }

    @Override
    protected int getLayoutId(int viewType) {
        return R.layout.layout_item_download_source;
    }

    @Override
    protected ViewHolder getViewHolder(View itemView, int viewType) {
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        DownloadSource model = data.get(position);
        holder.icon.setImageResource(model.isSourceCode() ? R.drawable.ic_source : R.drawable.ic_collection);
        holder.name.setText(model.getName());
        String meta = model.getSize() != 0 ? StringUtils.getSizeString(model.getSize()) : "";
        if (model.getDownloadCount() >= 0) {
            String downloads = context.getResources().getQuantityString(R.plurals.release_downloads,
                    model.getDownloadCount(), model.getDownloadCount());
            meta = meta.isEmpty() ? downloads : meta + " · " + downloads;
        }
        holder.size.setText(meta);
        holder.size.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
    }

    class ViewHolder extends BaseViewHolder {

        AppCompatImageView icon;
        TextView name;
        TextView size;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void onNameLongClick() {
            DownloadSource source = data.get(getAdapterPosition());
            AppUtils.copyToClipboard(context, source.getUrl());
        }

        public void onDownloadClick(){
            DownloadSource source = data.get(getAdapterPosition());
            String fileName = repoName.concat("-").concat(tagName)
                    .concat("-").concat(source.getName());
            AppOpener.startDownload(context, source.getUrl(), fileName);
        }


        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            icon = root.findViewById(R.id.icon);
            name = root.findViewById(R.id.name);
            size = root.findViewById(R.id.size);
            ViewUtils.setOnClick(root, R.id.download_bn, v -> onDownloadClick());
            ViewUtils.setOnClick(root, R.id.row, v -> onDownloadClick());
            name.setOnLongClickListener(v -> {
                onNameLongClick();
                return true;
            });
        }
    }

}
