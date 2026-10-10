package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

/**
 * A GitHub Actions workflow (one YAML file in .github/workflows).
 */
public class Workflow {

    private long id;
    private String name;
    private String path;
    /** active, disabled_manually, disabled_inactivity, deleted, ... */
    private String state;
    @SerializedName("html_url") private String htmlUrl;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getState() {
        return state;
    }

    public boolean isActive() {
        return "active".equals(state);
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    /** File name without the .github/workflows/ prefix. */
    public String getFileName() {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
