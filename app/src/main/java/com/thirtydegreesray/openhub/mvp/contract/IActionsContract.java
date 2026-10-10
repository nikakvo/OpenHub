package com.thirtydegreesray.openhub.mvp.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.Artifact;
import com.thirtydegreesray.openhub.mvp.model.Workflow;
import com.thirtydegreesray.openhub.mvp.model.WorkflowRun;

import java.util.ArrayList;
import java.util.Map;

/**
 * GitHub Actions: workflow runs of a repository, their artifacts, and the
 * repository's workflows (filter, manual start).
 */
public interface IActionsContract {

    interface View extends IBaseContract.View {
        void showRuns(ArrayList<WorkflowRun> runs);

        void setCanLoadMore(boolean canLoadMore);

        void showLoadError(String error);

        void showArtifacts(WorkflowRun run, ArrayList<Artifact> artifacts);

        /** forRun: the user wants to start a workflow, otherwise filter the runs. */
        void showWorkflowPicker(ArrayList<Workflow> workflows, boolean forRun);

        void onWorkflowFilterChanged(@Nullable String workflowName);

        void onWorkflowDispatched(@NonNull String workflowName, @NonNull String ref);
    }

    interface Presenter extends IBaseContract.Presenter<IActionsContract.View> {
        void loadRuns(int page, boolean isReload);

        void setSuccessfulOnly(boolean successfulOnly);

        boolean isSuccessfulOnly();

        void loadArtifacts(WorkflowRun run);

        void pickWorkflow(boolean forRun);

        /** null shows the runs of all workflows. */
        void selectWorkflow(@Nullable Workflow workflow);

        void dispatchWorkflow(@NonNull Workflow workflow, @NonNull String ref,
                              @NonNull Map<String, String> inputs);
    }

}
