package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class ArtifactsResult {

    @SerializedName("total_count") private int totalCount;
    private ArrayList<Artifact> artifacts;

    public int getTotalCount() {
        return totalCount;
    }

    public ArrayList<Artifact> getArtifacts() {
        return artifacts == null ? new ArrayList<>() : artifacts;
    }
}
