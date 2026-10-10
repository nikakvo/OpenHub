package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

/**
 * One file of a gist. "content" is only filled by GET /gists/{id}, and cut off
 * (truncated = true) for big files; raw_url always has the whole file.
 */
public class GistFile {

    private String filename;
    private String type;
    private String language;
    @SerializedName("raw_url") private String rawUrl;
    private long size;
    private boolean truncated;
    private String content;

    public String getFilename() {
        return filename;
    }

    public String getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public long getSize() {
        return size;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getContent() {
        return content;
    }
}
