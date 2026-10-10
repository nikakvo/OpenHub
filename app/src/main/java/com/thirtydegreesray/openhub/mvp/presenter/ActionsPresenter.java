package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.core.HttpSubscriber;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IActionsContract;
import com.thirtydegreesray.openhub.mvp.model.ArtifactsResult;
import com.thirtydegreesray.openhub.mvp.model.Workflow;
import com.thirtydegreesray.openhub.mvp.model.WorkflowRun;
import com.thirtydegreesray.openhub.mvp.model.WorkflowRunsResult;
import com.thirtydegreesray.openhub.mvp.model.WorkflowsResult;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import okhttp3.ResponseBody;

/**
 * Loads the workflow runs of one repository page by page, newest first, and the
 * artifacts of a run on demand. Works for any repository the user can read,
 * public ones included.
 */
public class ActionsPresenter extends BasePresenter<IActionsContract.View>
        implements IActionsContract.Presenter {

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String defaultBranch;
    @AutoAccess boolean successfulOnly = false;
    /** 0 = all workflows */
    @AutoAccess long workflowId = 0;
    @AutoAccess String workflowName;

    private ArrayList<WorkflowRun> runs;
    private ArrayList<Workflow> workflows;

    @Inject
    public ActionsPresenter(DaoSession daoSession) {
        super(daoSession);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if (workflowName != null) mView.onWorkflowFilterChanged(workflowName);
        if (runs == null) {
            loadRuns(1, false);
        } else {
            mView.showRuns(runs);
            mView.hideLoading();
        }
    }

    @Override
    public void loadRuns(final int page, final boolean isReload) {
        mView.showLoading();
        final boolean readCacheFirst = page == 1 && !isReload;
        HttpObserver<WorkflowRunsResult> observer = new HttpObserver<WorkflowRunsResult>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                if (!StringUtils.isBlankList(runs) && page > 1) {
                    mView.showErrorToast(getErrorTip(error));
                } else if (error instanceof HttpPageNoFoundError) {
                    runs = new ArrayList<>();
                    mView.showRuns(runs);
                } else {
                    mView.showLoadError(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<WorkflowRunsResult> response) {
                mView.hideLoading();
                ArrayList<WorkflowRun> pageRuns = response.body() == null
                        ? new ArrayList<>() : response.body().getWorkflowRuns();
                if (isReload || runs == null || readCacheFirst || page == 1) {
                    runs = pageRuns;
                } else {
                    runs.addAll(pageRuns);
                }
                if (pageRuns.size() == 0 && runs.size() != 0) {
                    mView.setCanLoadMore(false);
                } else {
                    mView.showRuns(runs);
                }
            }
        };
        final String status = successfulOnly ? "success" : null;
        final long workflow = workflowId;
        generalRxHttpExecute(forceNetWork -> workflow == 0
                ? getRepoService().getWorkflowRuns(forceNetWork, owner, repo, status, page)
                : getRepoService().getWorkflowRunsOf(forceNetWork, owner, repo, workflow, status, page),
                observer, readCacheFirst);
    }

    @Override
    public void setSuccessfulOnly(boolean successfulOnly) {
        if (this.successfulOnly == successfulOnly) return;
        this.successfulOnly = successfulOnly;
        runs = null;
        loadRuns(1, true);
    }

    @Override
    public boolean isSuccessfulOnly() {
        return successfulOnly;
    }

    @Override
    public void loadArtifacts(@NonNull final WorkflowRun run) {
        mView.showProgressDialog(getLoadTip());
        HttpObserver<ArtifactsResult> observer = new HttpObserver<ArtifactsResult>() {
            @Override
            public void onError(Throwable error) {
                mView.dismissProgressDialog();
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ArtifactsResult> response) {
                mView.dismissProgressDialog();
                mView.showArtifacts(run, response.body() == null
                        ? new ArrayList<>() : response.body().getArtifacts());
            }
        };
        generalRxHttpExecute(forceNetWork -> getRepoService().getRunArtifacts(true,
                owner, repo, run.getId()), observer);
    }

    @Override
    public void pickWorkflow(final boolean forRun) {
        if (workflows != null) {
            mView.showWorkflowPicker(workflows, forRun);
            return;
        }
        mView.showProgressDialog(getLoadTip());
        HttpObserver<WorkflowsResult> observer = new HttpObserver<WorkflowsResult>() {
            @Override
            public void onError(Throwable error) {
                mView.dismissProgressDialog();
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<WorkflowsResult> response) {
                mView.dismissProgressDialog();
                workflows = response.body() == null ? new ArrayList<>() : response.body().getWorkflows();
                mView.showWorkflowPicker(workflows, forRun);
            }
        };
        generalRxHttpExecute(forceNetWork -> getRepoService().getWorkflows(true, owner, repo), observer);
    }

    @Override
    public void selectWorkflow(@Nullable Workflow workflow) {
        long newId = workflow == null ? 0 : workflow.getId();
        if (newId == workflowId) return;
        workflowId = newId;
        workflowName = workflow == null ? null : workflow.getName();
        mView.onWorkflowFilterChanged(workflowName);
        runs = null;
        loadRuns(1, true);
    }

    @Override
    public void dispatchWorkflow(@NonNull final Workflow workflow, @NonNull final String ref,
                                 @NonNull Map<String, String> inputs) {
        Map<String, Object> body = new HashMap<>();
        body.put("ref", ref);
        if (!inputs.isEmpty()) body.put("inputs", inputs);
        mView.showProgressDialog(getLoadTip());
        HttpSubscriber<ResponseBody> subscriber = new HttpSubscriber<>(new HttpObserver<ResponseBody>() {
            @Override
            public void onError(Throwable error) {
                mView.dismissProgressDialog();
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ResponseBody> response) {
                mView.dismissProgressDialog();
                if (response.isSuccessful()) {
                    mView.onWorkflowDispatched(workflow.getName(), ref);
                } else {
                    mView.showErrorToast(readErrorMessage(response));
                }
            }
        });
        generalRxHttpExecute(getRepoService().dispatchWorkflow(owner, repo, workflow.getId(), body),
                subscriber);
    }

    /** GitHub's own explanation, e.g. "Workflow does not have 'workflow_dispatch' trigger". */
    private String readErrorMessage(HttpResponse<?> response) {
        String fallback = response.getOriResponse().code() + " " + response.getOriResponse().message();
        try {
            ResponseBody errorBody = response.getOriResponse().errorBody();
            if (errorBody == null) return fallback;
            JsonObject json = JsonParser.parseString(errorBody.string()).getAsJsonObject();
            return json.has("message") ? json.get("message").getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public String getOwner() {
        return owner;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getRepoName() {
        return repo;
    }

}
