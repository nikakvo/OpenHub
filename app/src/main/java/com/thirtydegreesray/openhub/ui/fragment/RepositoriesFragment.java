

package com.thirtydegreesray.openhub.ui.fragment;

import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.util.AppUtils;
import android.view.inputmethod.EditorInfo;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.TextView;
import android.widget.EditText;
import androidx.core.os.BundleCompat;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.navigation.NavigationView;
import android.view.MenuItem;
import android.view.View;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IRepositoriesContract;
import com.thirtydegreesray.openhub.mvp.model.Collection;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.SearchModel;
import com.thirtydegreesray.openhub.mvp.model.Topic;
import com.thirtydegreesray.openhub.mvp.model.TrendingLanguage;
import com.thirtydegreesray.openhub.mvp.model.filter.RepositoriesFilter;
import com.thirtydegreesray.openhub.mvp.model.filter.TrendingSince;
import com.thirtydegreesray.openhub.mvp.presenter.RepositoriesPresenter;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.ui.activity.TrendingActivity;
import com.thirtydegreesray.openhub.ui.adapter.RepositoriesAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.AccountChanges;
import com.thirtydegreesray.openhub.ui.fragment.base.OnDrawerSelectedListener;
import com.thirtydegreesray.openhub.util.BundleHelper;

import java.util.ArrayList;

/**
 * Created on 2017/7/18.
 *
 * @author ThirtyDegreesRay
 */

public class RepositoriesFragment extends ListFragment<RepositoriesPresenter, RepositoriesAdapter>
            implements IRepositoriesContract.View, OnDrawerSelectedListener,
        TrendingActivity.LanguageUpdateListener{

    public enum RepositoriesType{
        OWNED, PUBLIC, STARRED, TRENDING, SEARCH, FORKS, COLLECTION, TOPIC
    }

    public static RepositoriesFragment create(@NonNull RepositoriesType type,
                                              @NonNull String user){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(BundleHelper.builder().put("type", type)
                .put("user", user).build());
        return fragment;
    }

    public static RepositoriesFragment createForCollection(@NonNull Collection collection){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(BundleHelper.builder()
                .put("type", RepositoriesType.COLLECTION)
                .put("collection", collection)
                .build());
        return fragment;
    }

    public static RepositoriesFragment createForTopic(@NonNull Topic topic){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(BundleHelper.builder()
                .put("type", RepositoriesType.TOPIC)
                .put("topic", topic)
                .build());
        return fragment;
    }

    public static RepositoriesFragment createForForks(@NonNull String user, @NonNull String repo){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(BundleHelper.builder()
                .put("type", RepositoriesType.FORKS)
                .put("user", user)
                .put("repo", repo)
                .build());
        return fragment;
    }

    public static RepositoriesFragment createForSearch(@NonNull SearchModel searchModel){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(
                BundleHelper.builder()
                        .put("type", RepositoriesType.SEARCH)
                        .put("searchModel", searchModel)
                        .build()
        );
        return fragment;
    }

    public static RepositoriesFragment createForTrending(@NonNull TrendingSince since){
        RepositoriesFragment fragment = new RepositoriesFragment();
        fragment.setArguments(
                BundleHelper.builder()
                        .put("type", RepositoriesType.TRENDING)
                        .put("since", since)
                        .build()
        );
        return fragment;
    }

    @Override
    public void showRepositories(ArrayList<Repository> repositoryList) {
        adapter.setData(repositoryList);
        postNotifyDataSetChanged();
    }

    @Override
    protected int getLayoutId() {
        return hasFilterField() ? R.layout.fragment_repo_list : R.layout.fragment_list;
    }

    /** My, starred and a user's repositories get the filter field (read before the presenter). */
    private boolean hasFilterField() {
        RepositoriesType type = getArguments() == null ? null
                : BundleCompat.getSerializable(getArguments(), "type", RepositoriesType.class);
        return RepositoriesType.OWNED.equals(type) || RepositoriesType.PUBLIC.equals(type)
                || RepositoriesType.STARRED.equals(type);
    }

    @Nullable private EditText filterEdit;
    @Nullable private View filterClear;
    @Nullable private TextView filterStatus;

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        filterEdit = root.findViewById(R.id.repo_filter);
        filterClear = root.findViewById(R.id.repo_filter_clear);
        filterStatus = root.findViewById(R.id.repo_filter_status);
        if (filterEdit == null) return;
        filterEdit.setHint(filterHint());
        filterEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (filterClear != null) filterClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (mPresenter != null) mPresenter.setQuery(s.toString());
            }
        });
        filterEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                AppUtils.hideKeyboard(v);
                return true;
            }
            return false;
        });
        if (filterClear != null) filterClear.setOnClickListener(v -> filterEdit.setText(""));
    }

    /** The hint says which list the field filters. */
    @NonNull
    private String filterHint() {
        Bundle args = getArguments();
        RepositoriesType type = args == null ? null
                : BundleCompat.getSerializable(args, "type", RepositoriesType.class);
        String user = args == null ? null : args.getString("user");
        User me = AppData.INSTANCE.getLoggedUser();
        boolean mine = user == null || (me != null && user.equalsIgnoreCase(me.getLogin()));
        if (RepositoriesType.STARRED.equals(type)) {
            return mine ? getString(R.string.repo_filter_hint_my_stars)
                    : getString(R.string.repo_filter_hint_user_stars, user);
        }
        if (RepositoriesType.PUBLIC.equals(type) && !mine) {
            return getString(R.string.repo_filter_hint_user, user);
        }
        return getString(R.string.repo_filter_hint_mine);
    }

    @Override
    public void setFiltering(boolean filtering) {
        setLoadMoreEnable(!filtering);
        if (filtering) setCanLoadMore(false);
    }

    @Override
    public void showFilterStatus(@Nullable String status) {
        if (filterStatus == null) return;
        filterStatus.setText(status);
        filterStatus.setVisibility(status == null ? View.GONE : View.VISIBLE);
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
    protected void initFragment(Bundle savedInstanceState){
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(!RepositoriesType.COLLECTION.equals(mPresenter.getType()));
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadRepositories(true, 1);
    }

    @Override
    protected long changeStamp() {
        if (mPresenter == null) return 0;
        if (RepositoriesType.OWNED.equals(mPresenter.getType())) return AccountChanges.reposStamp();
        if (RepositoriesType.STARRED.equals(mPresenter.getType())) return AccountChanges.starsStamp();
        return 0;
    }

    @Override
    protected long autoRefreshAfterMs() {
        if (mPresenter == null) return 0;
        return RepositoriesType.OWNED.equals(mPresenter.getType())
                || RepositoriesType.STARRED.equals(mPresenter.getType())
                ? AUTO_REFRESH_LIST_MS : 0;
    }

    @Override
    protected String getEmptyTip() {
        if(RepositoriesType.TRENDING.equals(mPresenter.getType())){
            return String.format(getString(R.string.no_trending_repos), mPresenter.getLanguage().getName());
        }
        if (mPresenter.isFilterable() && !mPresenter.getQuery().isEmpty()) {
            return getString(R.string.repo_filter_no_match, mPresenter.getQuery());
        }
        return getString(R.string.no_repository);
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        if(RepositoriesType.TRENDING.equals(mPresenter.getType())
                || RepositoriesType.COLLECTION.equals(mPresenter.getType())){
            RepositoryActivity.show(getActivity(), adapter.getData().get(position).getOwner().getLogin(),
                    adapter.getData().get(position).getName());
        } else {
            RepositoryActivity.show(getActivity(), adapter.getData().get(position));
        }
    }

    @Override
    protected void onLoadMore(int page) {
        super.onLoadMore(page);
        mPresenter.loadRepositories(false, page);
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(mPresenter != null) mPresenter.prepareLoadData();
    }

    @Override
    public void onDrawerSelected(@NonNull NavigationView navView, @NonNull MenuItem item) {
        RepositoriesFilter filter = RepositoriesFilter.generateFromDrawer(navView);
        mPresenter.loadRepositories(filter);
    }

    @Override
    public void onLanguageUpdate(TrendingLanguage language) {
        if(mPresenter != null){
            mPresenter.setLanguage(language);
            mPresenter.setLoaded(false);
            mPresenter.prepareLoadData();
        } else {
            getArguments().putParcelable("language", language);
        }
    }

}
