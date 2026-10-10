package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * A build artifact of a workflow run. Downloading archive_download_url needs a token;
 * the API answers with a redirect to a short-lived zip URL.
 */
public class Artifact {

    private long id;
    private String name;
    @SerializedName("size_in_bytes") private long sizeInBytes;
    private boolean expired;
    @SerializedName("archive_download_url") private String archiveDownloadUrl;
    @SerializedName("created_at") private Date createdAt;
    @SerializedName("expires_at") private Date expiresAt;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getArchiveDownloadUrl() {
        return archiveDownloadUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }
}
