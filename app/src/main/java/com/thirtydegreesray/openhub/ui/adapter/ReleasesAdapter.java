package com.thirtydegreesray.openhub.ui.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.StringUtils;

import javax.inject.Inject;


/**
 * Created by ThirtyDegreesRay on 2017/9/16 12:14:02
 */

public class ReleasesAdapter extends BaseAdapter<ReleasesAdapter.ViewHolder, Release> {

    @Inject
    public ReleasesAdapter(Context context, BaseFragment fragment) {
        super(context, fragment);
    }

    @Override
    protected int getLayoutId(int viewType) {
        return R.layout.layout_item_release;
    }

    @Override
    protected ViewHolder getViewHolder(View itemView, int viewType) {
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Release model = data.get(position);
        String tag = StringUtils.isBlank(model.getTagName())
                ? context.getString(R.string.release_draft_badge) : model.getTagName();
        holder.releaseName.setText(tag);
        String title = model.getName();
        boolean showTitle = !StringUtils.isBlank(title) && !title.equals(model.getTagName());
        holder.title.setText(showTitle ? title : "");
        holder.title.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        if (model.isDraft()) {
            holder.badge.setText(R.string.release_draft_badge);
            holder.badge.setVisibility(View.VISIBLE);
        } else if (model.isPreRelease()) {
            holder.badge.setText(R.string.release_prerelease_badge);
            holder.badge.setVisibility(View.VISIBLE);
        } else if (position == getLatestPosition()) {
            holder.badge.setText(R.string.release_latest_badge);
            holder.badge.setVisibility(View.VISIBLE);
        } else {
            holder.badge.setVisibility(View.GONE);
        }
        holder.time.setText(model.getDisplayDate() == null ? ""
                : StringUtils.getNewsTimeStr(context, model.getDisplayDate()));
        if(!StringUtils.isBlank(model.getBodyHtml())){
            holder.body.setText(Html.fromHtml(model.getBodyHtml()));
            holder.body.setVisibility(View.VISIBLE);
        } else {
            holder.body.setText("");
            holder.body.setVisibility(View.GONE);
        }
    }

    /** The newest release that is not a draft or pre-release (what GitHub calls Latest). */
    private int getLatestPosition() {
        if (data == null) return -1;
        for (int i = 0; i < data.size(); i++) {
            Release release = data.get(i);
            if (!release.isDraft() && !release.isPreRelease()) return i;
        }
        return -1;
    }

    class ViewHolder extends BaseViewHolder {

        TextView releaseName;
        TextView title;
        TextView badge;
        TextView time;
        TextView body;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            releaseName = root.findViewById(R.id.release_name);
            title = root.findViewById(R.id.release_title);
            badge = root.findViewById(R.id.release_badge);
            time = root.findViewById(R.id.time);
            body = root.findViewById(R.id.body);
        }
    }

}
