

package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.mvp.contract.ISearchContract;
import com.thirtydegreesray.openhub.mvp.model.SearchModel;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.SearchHistory;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;

import javax.inject.Inject;

/**
 * Created by ThirtyDegreesRay on 2017/8/25 14:56:23
 */

public class SearchPresenter extends BasePresenter<ISearchContract.View>
        implements ISearchContract.Presenter {

    @AutoAccess ArrayList<SearchModel> searchModels;

    @Inject
    public SearchPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if (searchModels == null) {
            createSearchModels();
        } else {
            mView.showSearches(searchModels);
        }
    }

    private void createSearchModels() {
        searchModels = new ArrayList<>();
        searchModels.add(new SearchModel(SearchModel.SearchType.Repository));
        searchModels.add(new SearchModel(SearchModel.SearchType.User));
    }

    public ArrayList<SearchModel> getSearchModels() {
        return searchModels;
    }

    @Override
    public ArrayList<SearchModel> getQueryModels(@NonNull String query) {
        for (SearchModel searchModel : searchModels) {
            searchModel.setQuery(query);
        }
        return searchModels;
    }

    @Override
    public SearchModel getSortModel(int page, int sortId) {
        return searchModels.get(page).setSortId(sortId);
    }

    @NonNull
    @Override
    public ArrayList<String> getSearchRecordList() {
        return SearchHistory.parse(PrefUtils.getSearchRecords());
    }

    @Override
    public void addSearchRecord(@NonNull String record) {
        saveRecords(SearchHistory.add(getSearchRecordList(), record));
    }

    @NonNull
    @Override
    public ArrayList<String> removeSearchRecord(@NonNull String record) {
        ArrayList<String> list = SearchHistory.remove(getSearchRecordList(), record);
        saveRecords(list);
        return list;
    }

    @Override
    public void clearSearchRecords() {
        saveRecords(new ArrayList<>());
    }

    private static void saveRecords(@NonNull ArrayList<String> list) {
        PrefUtils.set(PrefUtils.SEARCH_RECORDS, SearchHistory.join(list));
    }

}
