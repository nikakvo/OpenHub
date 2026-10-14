package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.dao.Bookmark;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.dao.LocalRepo;
import com.thirtydegreesray.openhub.dao.LocalUser;
import com.thirtydegreesray.openhub.mvp.contract.IBookmarkContract;
import com.thirtydegreesray.openhub.mvp.model.BookmarkExt;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.LocalChanges;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by ThirtyDegreesRay on 2017/11/22 15:59:45
 */

public class BookmarkPresenter extends BasePresenter<IBookmarkContract.View>
        implements IBookmarkContract.Presenter {

    private ArrayList<BookmarkExt> bookmarks;
    private BookmarkExt removedBookmark;
    private int removedPosition;
    private long seenStamp;

    private static final int PAGE_SIZE = 30;

    @Inject
    public BookmarkPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        loadBookmarks(1);
    }

    @Override
    public void loadBookmarks(int page) {
        mView.showLoading();
        if (page == 1) seenStamp = LocalChanges.bookmarksStamp();
        final ArrayList<BookmarkExt> tempBookmarks = new ArrayList<>();
        Completable.fromAction(() -> db.runInTransaction(() -> {
                    List<Bookmark> bookmarkList =
                            db.bookmarkDao().page((page - 1) * PAGE_SIZE, PAGE_SIZE);
                    for(Bookmark bookmark : bookmarkList){
                        BookmarkExt ext = BookmarkExt.generate(bookmark);
                        if("user".equals(bookmark.getType())){
                            LocalUser localUser = db.localUserDao().load(ext.getUserId());
                            if (localUser == null) continue; // no local copy: cannot be shown
                            ext.setUser(User.generateFromLocalUser(localUser));
                        }else{
                            LocalRepo localRepo = ext.getRepoId() == null ? null
                                    : db.localRepoDao().load(ext.getRepoId());
                            if (localRepo == null) continue;
                            ext.setRepository(Repository.generateFromLocalRepo(localRepo));
                        }
                        tempBookmarks.add(ext);
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (mView == null) return;
                    if(bookmarks == null || page == 1){
                        bookmarks = tempBookmarks;
                    } else {
                        bookmarks.addAll(tempBookmarks);
                    }
                    mView.showBookmarks(bookmarks);
                    mView.hideLoading();
                }, error -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    mView.showErrorToast(getErrorTip(error));
                });

    }

    @Override
    public void removeBookmark(int position) {
        removedBookmark = bookmarks.remove(position);
        removedPosition = position;
        rxDBExecute(() -> db.bookmarkDao().deleteById(removedBookmark.getId()));
    }

    @Override
    public void undoRemoveBookmark() {
        bookmarks.add(removedPosition, removedBookmark);
        mView.notifyItemAdded(removedPosition);
        rxDBExecute(() -> db.bookmarkDao().insert(removedBookmark));
    }

    @Override
    public void reloadIfChanged() {
        if (LocalChanges.changedSince(seenStamp, LocalChanges.bookmarksStamp())) loadBookmarks(1);
    }

}
