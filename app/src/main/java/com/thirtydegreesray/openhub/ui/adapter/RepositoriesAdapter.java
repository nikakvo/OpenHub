

package com.thirtydegreesray.openhub.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.LanguageColorsHelper;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import javax.inject.Inject;


/**
 * Created on 2017/7/18.
 *
 * @author ThirtyDegreesRay
 */

public class RepositoriesAdapter extends BaseAdapter<RepositoriesAdapter.ViewHolder, Repository> {

    @Inject
    public RepositoriesAdapter(Context context, BaseFragment fragment){
        super(context, fragment);
    }

    @Override
    protected int getLayoutId(int viewType) {
        return R.layout.layout_item_repository;
    }

    @NonNull
    @Override
    protected ViewHolder getViewHolder(@NonNull View itemView, int viewType) {
        ViewHolder holder = new ViewHolder(itemView);
        return holder;
    }

    public class ViewHolder extends BaseViewHolder {

        ImageView ivUserAvatar;
        ImageView languageColor;
        TextView tvRepoName;
        TextView tvLanguage;
        TextView tvRepoDescription;
        TextView tvStarNum;
        TextView tvForkNum;
        TextView tvOwnerName;
        TextView tvSinceStarNum;
        LinearLayout sinceStarLay;
        LinearLayout ownerLay;
        View forkMark;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void onUserClick(){
            if(getAdapterPosition() != RecyclerView.NO_POSITION){
                ProfileActivity.show((Activity) context, ivUserAvatar, data.get(getAdapterPosition()).getOwner().getLogin(),
                        data.get(getAdapterPosition()).getOwner().getAvatarUrl());
            }
        }

        @Override
        protected void bindViews(View root) {
            super.bindViews(root);
            ivUserAvatar = root.findViewById(R.id.iv_user_avatar);
            languageColor = root.findViewById(R.id.language_color);
            tvRepoName = root.findViewById(R.id.tv_repo_name);
            tvLanguage = root.findViewById(R.id.tv_language);
            tvRepoDescription = root.findViewById(R.id.tv_repo_description);
            tvStarNum = root.findViewById(R.id.tv_star_num);
            tvForkNum = root.findViewById(R.id.tv_fork_num);
            tvOwnerName = root.findViewById(R.id.tv_owner_name);
            tvSinceStarNum = root.findViewById(R.id.tv_since_star_num);
            sinceStarLay = root.findViewById(R.id.since_star_lay);
            ownerLay = root.findViewById(R.id.owner_lay);
            forkMark = root.findViewById(R.id.fork_mark);
            ViewUtils.setOnClick(root, R.id.iv_user_avatar, v -> onUserClick());
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Repository repository = data.get(position);
        boolean hasOwnerAvatar = !StringUtils.isBlank(repository.getOwner().getAvatarUrl());
        holder.tvRepoName.setText(hasOwnerAvatar ? repository.getName(): repository.getFullName());
        ViewUtils.setTextView(holder.tvRepoDescription, repository.getDescription());
        holder.tvStarNum.setText(String.valueOf(repository.getStargazersCount()));
        holder.tvForkNum.setText(String.valueOf(repository.getForksCount()));
        holder.tvOwnerName.setText(repository.getOwner().getLogin());

        if(StringUtils.isBlank(repository.getLanguage())){
            holder.tvLanguage.setText("");
            holder.languageColor.setVisibility(View.INVISIBLE);
        } else {
            holder.languageColor.setVisibility(View.VISIBLE);
            holder.tvLanguage.setText(repository.getLanguage());
            int languageColor = LanguageColorsHelper.INSTANCE.getColor(context, repository.getLanguage());
            holder.languageColor.setImageTintList(ColorStateList.valueOf(languageColor));
        }


        if(hasOwnerAvatar){
            holder.ivUserAvatar.setVisibility(View.VISIBLE);
            holder.ownerLay.setVisibility(View.VISIBLE);
            holder.sinceStarLay.setVisibility(View.GONE);
            GlideApp.with(fragment)
                    .load(repository.getOwner().getAvatarUrl())
                    .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                    .into(holder.ivUserAvatar);
        } else {
            holder.ivUserAvatar.setVisibility(View.GONE);
            holder.ownerLay.setVisibility(View.GONE);
            if (repository.getSinceStargazersCount() == 0) {
                holder.sinceStarLay.setVisibility(View.INVISIBLE);
            } else {
                holder.sinceStarLay.setVisibility(View.VISIBLE);
                switch (repository.getSince()) {
                    case Daily:
                        holder.tvSinceStarNum.setText(String.format(getString(R.string.star_num_today),
                                repository.getSinceStargazersCount()));
                        break;
                    case Weekly:
                        holder.tvSinceStarNum.setText(String.format(getString(R.string.star_num_this_week),
                                repository.getSinceStargazersCount()));
                        break;
                    case Monthly:
                        holder.tvSinceStarNum.setText(String.format(getString(R.string.star_num_this_month),
                                repository.getSinceStargazersCount()));
                        break;
                }
            }
        }

        holder.forkMark.setVisibility(repository.isFork() ? View.VISIBLE : View.GONE);
    }
}
