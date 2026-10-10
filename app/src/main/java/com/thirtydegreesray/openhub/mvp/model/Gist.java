package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A GitHub gist (GET /users/{user}/gists, /gists, /gists/{id}).
 */
public class Gist {

    private String id;
    private String description;
    @SerializedName("public") private boolean isPublic;
    @SerializedName("html_url") private String htmlUrl;
    @SerializedName("created_at") private Date createdAt;
    @SerializedName("updated_at") private Date updatedAt;
    private int comments;
    private User owner;
    /** file name -> file, in the order GitHub returns them */
    private LinkedHashMap<String, GistFile> files;

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public int getComments() {
        return comments;
    }

    public User getOwner() {
        return owner;
    }

    public List<GistFile> getFileList() {
        return files == null ? new ArrayList<>() : new ArrayList<>(files.values());
    }

    /** Description, or the first file name when the gist has none. */
    public String getTitle() {
        if (description != null && !description.trim().isEmpty()) return description.trim();
        List<GistFile> list = getFileList();
        return list.isEmpty() ? id : list.get(0).getFilename();
    }
}
