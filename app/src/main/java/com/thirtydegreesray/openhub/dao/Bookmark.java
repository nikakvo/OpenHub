package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table BOOKMARK (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "BOOKMARK")
public class Bookmark {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "ID")
    private String id;

    @NonNull
    @ColumnInfo(name = "TYPE")
    private String type;

    @ColumnInfo(name = "USER_ID")
    private String userId;

    @ColumnInfo(name = "REPO_ID")
    private Long repoId;

    @ColumnInfo(name = "MARK_TIME")
    private java.util.Date markTime;

    public Bookmark() {
    }

    @Ignore
    public Bookmark(String id) {
        this.id = id;
    }

    @Ignore
    public Bookmark(String id, String type, String userId, Long repoId, java.util.Date markTime) {
        this.id = id;
        this.type = type;
        this.userId = userId;
        this.repoId = repoId;
        this.markTime = markTime;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getRepoId() {
        return repoId;
    }

    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }

    public java.util.Date getMarkTime() {
        return markTime;
    }

    public void setMarkTime(java.util.Date markTime) {
        this.markTime = markTime;
    }

}
