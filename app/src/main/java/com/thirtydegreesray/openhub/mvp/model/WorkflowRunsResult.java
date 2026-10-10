package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class WorkflowRunsResult {

    @SerializedName("total_count") private int totalCount;
    @SerializedName("workflow_runs") private ArrayList<WorkflowRun> workflowRuns;

    public int getTotalCount() {
        return totalCount;
    }

    public ArrayList<WorkflowRun> getWorkflowRuns() {
        return workflowRuns == null ? new ArrayList<>() : workflowRuns;
    }
}
