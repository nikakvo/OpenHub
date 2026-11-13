

package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.SearchView;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.AppEventBus;
import com.thirtydegreesray.openhub.common.Event;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.ISearchContract;
import com.thirtydegreesray.openhub.mvp.model.SearchModel;
import com.thirtydegreesray.openhub.mvp.presenter.SearchPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.PagerActivity;
import com.thirtydegreesray.openhub.ui.adapter.SearchHistoryAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.FragmentPagerModel;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;
import com.thirtydegreesray.openhub.ui.fragment.UserListFragment;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ThirtyDegreesRay on 2017/8/25 10:54:04
 */

public class SearchActivity extends PagerActivity<SearchPresenter>
        implements ISearchContract.View,
        MenuItem.OnActionExpandListener,
        SearchView.OnQueryTextListener {

    public static void show(@NonNull Context context) {
        Intent intent = new Intent(context, SearchActivity.class);
        context.startActivity(intent);
    }

    private final Map<Integer, List<Integer>> MENU_ID_MAP = new HashMap<>();

    @AutoAccess boolean isInputMode = true;
    @AutoAccess String[] sortInfos;

    @Override
    protected void initActivity() {
        super.initActivity();
        MENU_ID_MAP.put(0, SearchModel.REPO_SORT_ID_LIST);
        MENU_ID_MAP.put(1, SearchModel.USER_SORT_ID_LIST);
    }

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    @Nullable
    @Override
    protected int getContentView() {
        return R.layout.activity_view_pager;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarScrollAble(true);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.search));
        if(sortInfos == null)
        sortInfos = new String[]{
                getString(R.string.best_match), getString(R.string.best_match)
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView =
                (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(this);
        searchView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        searchView.setQuery(mPresenter.getSearchModels().get(0).getQuery(), false);
        if (isInputMode) {
            searchItem.expandActionView();
        } else {
            searchItem.collapseActionView();
        }
        searchItem.setOnActionExpandListener(this);

        AutoCompleteTextView autoCompleteTextView = searchView
                .findViewById(androidx.appcompat.R.id.search_src_text);
        autoCompleteTextView.setThreshold(0);
        historyBox = autoCompleteTextView;
        historyAdapter = new SearchHistoryAdapter(this, mPresenter.getSearchRecordList(),
                new SearchHistoryAdapter.Host() {
                    @Override
                    public void onRemoveSearch(@NonNull String search) {
                        removeSearch(search);
                    }

                    @Override
                    public void onClearSearches() {
                        confirmClearSearches();
                    }
                });
        autoCompleteTextView.setAdapter(historyAdapter);
        fitHistoryBelow(autoCompleteTextView);
        autoCompleteTextView.setDropDownBackgroundDrawable(new ColorDrawable(ViewUtils.getWindowBackground(getActivity())));
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getAdapter().getItem(position);
            if (item != null && !item.toString().isEmpty()) onQueryTextSubmit(item.toString());
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Nullable private SearchHistoryAdapter historyAdapter;
    @Nullable private AutoCompleteTextView historyBox;

    @Nullable private ViewTreeObserver.OnGlobalLayoutListener historyFitter;

    /**
     * The history drop-down is as tall as its rows, but never taller than the room between
     * the search box and the keyboard: taller, Android drew it over the search box itself
     * and nobody could type. Measured again when the keyboard shows or hides and when the
     * typed text filters the rows.
     */
    private void fitHistoryBelow(@NonNull AutoCompleteTextView box) {
        final View root = getWindow().getDecorView();
        removeHistoryFitter();
        historyFitter = this::fitHistory;
        root.getViewTreeObserver().addOnGlobalLayoutListener(historyFitter);
        if (historyAdapter != null) {
            historyAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    fitHistory();
                }
            });
        }
    }

    private void fitHistory() {
        AutoCompleteTextView box = historyBox;
        if (box == null || historyAdapter == null || !box.isShown()) return;
        float density = getResources().getDisplayMetrics().density;
        Rect frame = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        int[] location = new int[2];
        box.getLocationOnScreen(location);
        int room = frame.bottom - (location[1] + box.getHeight()) - Math.round(8 * density);
        TypedValue value = new TypedValue();
        int rowHeight = getTheme().resolveAttribute(android.R.attr.listPreferredItemHeightSmall, value, true)
                ? (int) value.getDimension(getResources().getDisplayMetrics()) : Math.round(48 * density);
        int rows = historyAdapter.getCount();
        if (rows == 0) return;
        int height = Math.max(Math.min(rowHeight * 2, rows * rowHeight), Math.min(rows * rowHeight, room));
        if (box.getDropDownHeight() != height) {
            box.setDropDownHeight(height);
            if (box.isPopupShowing()) box.showDropDown();
        }
    }

    private void removeHistoryFitter() {
        if (historyFitter == null) return;
        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(historyFitter);
        historyFitter = null;
    }

    @Override
    protected void onDestroy() {
        removeHistoryFitter();
        super.onDestroy();
    }

    /** Forgets one search; the drop-down stays open with the rest that match the typed text. */
    private void removeSearch(@NonNull String search) {
        ArrayList<String> rest = mPresenter.removeSearchRecord(search);
        if (historyAdapter == null || historyBox == null) return;
        historyAdapter.clear();
        historyAdapter.addAll(rest);
        if (rest.isEmpty()) {
            historyBox.dismissDropDown();
        } else {
            historyAdapter.getFilter().filter(historyBox.getText());
        }
    }

    private void confirmClearSearches() {
        int count = mPresenter.getSearchRecordList().size();
        if (count == 0) return;
        if (historyBox != null) historyBox.dismissDropDown();
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_history_clear)
                .setMessage(getResources().getQuantityString(R.plurals.search_history_clear_message, count, count))
                .setPositiveButton(R.string.search_history_clear_bn, (dialog, which) -> {
                    mPresenter.clearSearchRecords();
                    if (historyAdapter != null) historyAdapter.clear();
                    showSuccessToast(getString(R.string.search_history_cleared));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sort) {
            filterSortMenu(item);
        } else if (SearchModel.SORT_ID_LIST.contains(item.getItemId())) {
            int page = viewPager.getCurrentItem();
            postSearchEvent(mPresenter.getSortModel(page, item.getItemId()));
            sortInfos[page] = item.getTitle().toString();
            setSubTitle(page);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isInputMode) {
            menu.findItem(R.id.action_info).setVisible(false);
            menu.findItem(R.id.action_sort).setVisible(false);
            SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                    .getActionView();
            searchView.setQuery(mPresenter.getSearchModels().get(0).getQuery(), false);
        } else {
            menu.findItem(R.id.action_info).setVisible(false);
            menu.findItem(R.id.action_sort).setVisible(pagerAdapter.getCount() != 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        isInputMode = true;
        invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        if (pagerAdapter.getCount() == 0) {
            // Back before anything was searched: leave the screen instead of showing it empty
            finish();
            return true;
        }
        isInputMode = false;
        invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (StringUtils.isBlank(query)) {
            showWarningToast(getString(R.string.invalid_query));
            return true;
        }
        isInputMode = false;
        invalidateOptionsMenu();
        search(query);
        setSubTitle(viewPager.getCurrentItem());
        mPresenter.addSearchRecord(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private void filterSortMenu(MenuItem item) {
        int index = viewPager.getCurrentItem();
        List<Integer> idList = MENU_ID_MAP.get(index);
        for (Integer id : SearchModel.SORT_ID_LIST) {
            item.getSubMenu().findItem(id).setVisible(idList.contains(id));
        }
    }

    private void search(String query) {
        if (pagerAdapter.getCount() == 0) {
            pagerAdapter.setPagerList(FragmentPagerModel.
                    createSearchPagerList(getActivity(), mPresenter.getQueryModels(query), getFragments()));
            tabLayout.setVisibility(View.VISIBLE);
            setupPager();
            showFirstPager();
        } else {
            for (SearchModel searchModel : mPresenter.getQueryModels(query)) {
                postSearchEvent(searchModel);
            }
        }
    }

    private void postSearchEvent(SearchModel searchModel) {
        AppEventBus.INSTANCE.getEventBus().post(new Event.SearchEvent(searchModel));
    }

    private void setSubTitle(int page) {
        setToolbarSubTitle(mPresenter.getSearchModels().get(0).getQuery() + "/" + sortInfos[page]);
    }

    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        setSubTitle(position);
    }

    @Override
    public int getPagerSize() {
        return 2;
    }

    @Override
    public void showSearches(ArrayList<SearchModel> searchModels) {
        search(searchModels.get(0).getQuery());
        setSubTitle(0);
    }

    @Override
    protected int getFragmentPosition(Fragment fragment) {
        if(fragment instanceof RepositoriesFragment){
            return 0;
        }else if(fragment instanceof UserListFragment){
            return 1;
        }else
            return -1;
    }

}
