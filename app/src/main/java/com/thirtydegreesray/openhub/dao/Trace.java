package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table TRACE (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "TRACE")
public class Trace {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "ID")
    private String id;

    @ColumnInfo(name = "TYPE")
    private String type;

    @ColumnInfo(name = "USER_ID")
    private String userId;

    @ColumnInfo(name = "REPO_ID")
    private Long repoId;

    @ColumnInfo(name = "START_TIME")
    private java.util.Date startTime;

    @ColumnInfo(name = "LATEST_TIME")
    private java.util.Date latestTime;

    @ColumnInfo(name = "TRACE_NUM")
    private Integer traceNum;

    public Trace() {
    }

    @Ignore
    public Trace(String id) {
        this.id = id;
    }

    @Ignore
    public Trace(String id, String type, String userId, Long repoId, java.util.Date startTime, java.util.Date latestTime, Integer traceNum) {
        this.id = id;
        this.type = type;
        this.userId = userId;
        this.repoId = repoId;
        this.startTime = startTime;
        this.latestTime = latestTime;
        this.traceNum = traceNum;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public java.util.Date getStartTime() {
        return startTime;
    }

    public void setStartTime(java.util.Date startTime) {
        this.startTime = startTime;
    }

    public java.util.Date getLatestTime() {
        return latestTime;
    }

    public void setLatestTime(java.util.Date latestTime) {
        this.latestTime = latestTime;
    }

    public Integer getTraceNum() {
        return traceNum;
    }

    public void setTraceNum(Integer traceNum) {
        this.traceNum = traceNum;
    }

}
