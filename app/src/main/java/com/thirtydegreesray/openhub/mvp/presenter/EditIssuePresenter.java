

package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.model.IssueRequestModel;
import com.thirtydegreesray.openhub.mvp.contract.IEditIssueContract;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.Label;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/10/14 16:49:20
 */

public class EditIssuePresenter extends BasePresenter<IEditIssueContract.View>
        implements IEditIssueContract.Presenter{

    @AutoAccess String userId;
    @AutoAccess String repoName;
    @AutoAccess Issue issue;
    @AutoAccess boolean addMode;

    private ArrayList<Label> allLabels;
    /** the user may set labels, assignees and the milestone (triage, write or admin) */
    @AutoAccess boolean canEditMeta;

    public boolean isCanEditMeta() {
        return canEditMeta;
    }

    public void setCanEditMeta(boolean canEditMeta) {
        this.canEditMeta = canEditMeta;
    }

    @Inject
    public EditIssuePresenter(AppDatabase db) {
        super(db);
        issue = new Issue();
    }

    public String getIssueTitle(){
        return issue.getTitle();
    }

    public String getIssueComment(){
        return issue.getBody();
    }

    public ArrayList<Label> getLabels(){
        return issue.getLabels();
    }

    @Override
    public void commitIssue(@NonNull String title, @NonNull String comment) {
        HttpObserver<Issue> httpObserver = new HttpObserver<Issue>() {
            @Override
            public void onError(Throwable error) {
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<Issue> response) {
                mView.showNewIssue(response.body());
            }
        };

        issue.setTitle(title);
        issue.setBody(comment);

        // labels and assignees by name, the milestone by number; null removes it
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("title", title);
        json.addProperty("body", comment);
        if (canEditMeta) {
            com.google.gson.JsonArray labels = new com.google.gson.JsonArray();
            if (issue.getLabels() != null) for (Label label : issue.getLabels()) labels.add(label.getName());
            json.add("labels", labels);
            com.google.gson.JsonArray assignees = new com.google.gson.JsonArray();
            if (issue.getAssignees() != null) {
                for (com.thirtydegreesray.openhub.mvp.model.User user : issue.getAssignees()) {
                    assignees.add(user.getLogin());
                }
            }
            json.add("assignees", assignees);
            if (issue.getMilestone() != null) json.addProperty("milestone", issue.getMilestone().getNumber());
            else if (!addMode) json.add("milestone", com.google.gson.JsonNull.INSTANCE);
        }
        String text = new com.google.gson.GsonBuilder().serializeNulls().create().toJson(json);
        okhttp3.RequestBody body = okhttp3.RequestBody.create(text,
                okhttp3.MediaType.get("application/json; charset=utf-8"));

        generalRxHttpExecute(new IObservableCreator<Issue>() {
            @Override
            public Observable<Response<Issue>> createObservable(boolean forceNetWork) {
                return addMode ? getIssueService().createIssueJson(userId, repoName, body) :
                        getIssueService().editIssueJson(getRepoOwner(), getRepoName(), issue.getNumber(), body);
            }
        }, httpObserver, false, mView.getProgressDialog(getLoadTip()));

    }

    @Override
    public void loadLabels() {
        if(allLabels != null){
            mView.onLoadLabelsComplete(getAllLabelsWithSelectFlag());
            return;
        }

        HttpObserver<ArrayList<Label>> httpObserver = new HttpObserver<ArrayList<Label>>() {
            @Override
            public void onError(Throwable error) {
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ArrayList<Label>> response) {
                allLabels = response.body();
                mView.onLoadLabelsComplete(getAllLabelsWithSelectFlag());
            }
        };

        generalRxHttpExecute(forceNetWork ->
                        getIssueService().getRepoLabels(forceNetWork, getRepoOwner(), getRepoName()),
                httpObserver, false, mView.getProgressDialog(getLoadTip()));
    }

    @Override
    public void clearAllLabelsData() {
        allLabels = null;
    }

    private ArrayList<Label> getAllLabelsWithSelectFlag(){
        for(Label label : allLabels){
            if(issue.getLabels() == null || !issue.getLabels().contains(label)){
                label.setSelect(false);
            } else {
                label.setSelect(true);
            }
        }
        return allLabels;
    }

    public boolean isAddMode() {
        return addMode;
    }

    public String getRepoOwner() {
        return addMode ? userId : issue.getRepoAuthorName();
    }

    public String getRepoName() {
        return addMode ? repoName : issue.getRepoName();
    }

    public Issue getIssue() {
        return issue;
    }

}
