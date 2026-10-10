package com.thirtydegreesray.openhub.ui.adapter;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.Gist;
import com.thirtydegreesray.openhub.mvp.model.GistFile;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.List;

import javax.inject.Inject;

public class GistsAdapter extends BaseAdapter<GistsAdapter.ViewHolder, Gist> {

    @Inject
    public GistsAdapter(Context context, BaseFragment fragment) {
        super(context, fragment);
    }

    @Override
    protected int getLayoutId(int viewType) {
        return R.layout.layout_item_gist;
    }

    @Override
    protected ViewHolder getViewHolder(View itemView, int viewType) {
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Gist gist = data.get(position);
        holder.title.setText(gist.getTitle());
        holder.secret.setVisibility(gist.isPublic() ? View.GONE : View.VISIBLE);

        List<GistFile> files = gist.getFileList();
        StringBuilder names = new StringBuilder(context.getResources().getQuantityString(
                R.plurals.gist_files, files.size(), files.size()));
        for (int i = 0; i < files.size() && i < 4; i++) {
            names.append(i == 0 ? ": " : ", ").append(files.get(i).getFilename());
        }
        if (files.size() > 4) names.append(", ...");
        holder.files.setText(names);

        holder.time.setText(gist.getUpdatedAt() == null ? ""
                : StringUtils.getNewsTimeStr(context, gist.getUpdatedAt()));
    }

    class ViewHolder extends BaseViewHolder {
        TextView title;
        TextView secret;
        TextView files;
        TextView time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            title = root.findViewById(R.id.gist_title);
            secret = root.findViewById(R.id.gist_secret);
            files = root.findViewById(R.id.gist_files);
            time = root.findViewById(R.id.gist_time);
        }
    }
}
