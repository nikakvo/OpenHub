

package com.thirtydegreesray.openhub.ui.fragment;

import com.thirtydegreesray.openhub.ui.activity.GistsActivity;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IProfileInfoContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.ProfileInfoPresenter;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.activity.RepoListActivity;
import com.thirtydegreesray.openhub.ui.activity.UserListActivity;
import com.thirtydegreesray.openhub.ui.adapter.UsersAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;

import javax.inject.Inject;


/**
 * Created by ThirtyDegreesRay on 2017/8/23 14:39:19
 */

public class ProfileInfoFragment extends BaseFragment<ProfileInfoPresenter>
        implements IProfileInfoContract.View,
        BaseViewHolder.OnItemClickListener{

    TextView name;
    TextView bio;
    TextView company;
    TextView email;
    TextView link;

    LinearLayout membersLay;
    LinearLayout followersLay;
    LinearLayout followingLay;
    LinearLayout gistsLay;
    TextView followersNumText;
    TextView followingNumText;
    TextView reposNumText;
    TextView gistsNumText;

    CardView orgsLay;
    RecyclerView orgsRecyclerView;

    @Inject UsersAdapter orgsAdapter;

    public static ProfileInfoFragment create(User user) {
        ProfileInfoFragment fragment = new ProfileInfoFragment();
        fragment.setArguments(BundleHelper.builder().put("user", user).build());
        return fragment;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_profile_info;
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
        ViewUtils.setLongClickCopy(email);
        ViewUtils.setLongClickCopy(link);
        orgsLay.setVisibility(View.GONE);
    }

    public void onViewClicked(View view) {
        int viewId = view.getId();
        if (viewId == R.id.followers_lay) {
            if(mPresenter.getUser().getFollowers() == 0) return;
            UserListActivity.show(getActivity(), UserListFragment.UserListType.FOLLOWERS,
                    mPresenter.getUser().getLogin());
        } else if (viewId == R.id.following_lay) {
            if(mPresenter.getUser().getFollowing() == 0) return;
            UserListActivity.show(getActivity(), UserListFragment.UserListType.FOLLOWING,
                    mPresenter.getUser().getLogin());
        } else if (viewId == R.id.repos_lay) {
            if(mPresenter.getUser().getPublicRepos() == 0) return;
            RepositoriesFragment.RepositoriesType type =
                    AppData.INSTANCE.getLoggedUser().getLogin().equals(mPresenter.getUser().getLogin()) ?
                    RepositoriesFragment.RepositoriesType.OWNED : RepositoriesFragment.RepositoriesType.PUBLIC;
            RepoListActivity.show(getContext(), type, mPresenter.getUser().getLogin());
        } else if (viewId == R.id.gists_lay) {
            GistsActivity.show(getActivity(), mPresenter.getUser().getLogin());
        } else if (viewId == R.id.email) {
            AppOpener.launchEmail(getActivity(), mPresenter.getUser().getEmail());
        } else if (viewId == R.id.link) {
            AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getUser().getBlog());
        } else if (viewId == R.id.members_lay) {
            UserListActivity.show(getActivity(), UserListFragment.UserListType.ORG_MEMBERS,
                    mPresenter.getUser().getLogin());
        }
    }

    @Override
    public void showProfileInfo(User user) {

        String nameStr = StringUtils.isBlank(user.getName()) ? user.getLogin() : user.getName();
        nameStr = user.isUser() ? nameStr : nameStr.concat("(ORG)");
        name.setText(nameStr);

        bio.setText(user.getBio());
        bio.setVisibility(StringUtils.isBlank(user.getBio()) ? View.GONE :View.VISIBLE);

        followersNumText.setText(String.valueOf(user.getFollowers()));
        followingNumText.setText(String.valueOf(user.getFollowing()));
        reposNumText.setText(String.valueOf(user.getPublicRepos()));
        gistsNumText.setText(String.valueOf(user.getPublicGists()));
        if(!user.isUser()){
            membersLay.setVisibility(View.VISIBLE);
            followersLay.setVisibility(View.GONE);
            followingLay.setVisibility(View.GONE);
            gistsLay.setVisibility(View.GONE);
        }else {
            membersLay.setVisibility(View.GONE);
        }

        ViewUtils.setTextView(company, user.getCompany());
        ViewUtils.setTextView(email, user.getEmail());
        ViewUtils.setTextView(link, user.getBlog());
    }

    @Override
    public void showUserOrgs(ArrayList<User> orgs) {
        if(orgsRecyclerView.getAdapter() == null){
            orgsLay.setVisibility(View.VISIBLE);
            orgsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            orgsRecyclerView.setAdapter(orgsAdapter);
            orgsAdapter.setOnItemClickListener(this);
            orgsAdapter.setCardEnable(false);
        }
        orgsAdapter.setData(orgs);
        orgsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        View userAvatar = view.findViewById(R.id.avatar);
        ProfileActivity.show((Activity) getContext(), userAvatar, orgsAdapter.getData().get(position).getLogin(),
                orgsAdapter.getData().get(position).getAvatarUrl());
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(mPresenter != null) mPresenter.prepareLoadData();
    }

    public void updateProfileInfo(User user){
        mPresenter.setUser(user);
        showProfileInfo(user);
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        name = root.findViewById(R.id.name);
        bio = root.findViewById(R.id.bio);
        company = root.findViewById(R.id.company);
        email = root.findViewById(R.id.email);
        link = root.findViewById(R.id.link);
        membersLay = root.findViewById(R.id.members_lay);
        followersLay = root.findViewById(R.id.followers_lay);
        followingLay = root.findViewById(R.id.following_lay);
        gistsLay = root.findViewById(R.id.gists_lay);
        followersNumText = root.findViewById(R.id.followers_num_text);
        followingNumText = root.findViewById(R.id.following_num_text);
        reposNumText = root.findViewById(R.id.repos_num_text);
        gistsNumText = root.findViewById(R.id.gists_num_text);
        orgsLay = root.findViewById(R.id.orgs_lay);
        orgsRecyclerView = root.findViewById(R.id.orgs_recycler_view);
        ViewUtils.setOnClick(root, R.id.followers_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.following_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.repos_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.gists_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.email, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.link, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.members_lay, v -> onViewClicked(v));
    }
}
