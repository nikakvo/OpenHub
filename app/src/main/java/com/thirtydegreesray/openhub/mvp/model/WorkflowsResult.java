package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class WorkflowsResult {

    @SerializedName("total_count") private int totalCount;
    private ArrayList<Workflow> workflows;

    public int getTotalCount() {
        return totalCount;
    }

    public ArrayList<Workflow> getWorkflows() {
        return workflows == null ? new ArrayList<>() : workflows;
    }
}
