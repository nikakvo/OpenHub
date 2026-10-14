

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.mvp.contract.ICommitFilesContract;
import com.thirtydegreesray.openhub.mvp.model.CommitFile;
import com.thirtydegreesray.openhub.mvp.model.CommitFilesPathModel;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.ui.adapter.base.DoubleTypesModel;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by ThirtyDegreesRay on 2017/10/18 15:22:34
 */

public class CommitFilesPresenter extends BasePresenter<ICommitFilesContract.View>
        implements ICommitFilesContract.Presenter {

    @Inject
    public CommitFilesPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public ArrayList<DoubleTypesModel<CommitFilesPathModel, CommitFile>> getSortedList(
            ArrayList<CommitFile> commitFiles) {
        ArrayList<DoubleTypesModel<CommitFilesPathModel, CommitFile>>  list = new ArrayList<>();
        String preBasePath = "";
        for(CommitFile commitFile : commitFiles){
            if(!preBasePath.equals(commitFile.getBasePath())){
                list.add(new DoubleTypesModel<CommitFilesPathModel, CommitFile>(
                        new CommitFilesPathModel().setPath(commitFile.getBasePath()), null));
                preBasePath = commitFile.getBasePath();
            }
            list.add(new DoubleTypesModel<CommitFilesPathModel, CommitFile>(null, commitFile));
        }
        return list;
    }
}
