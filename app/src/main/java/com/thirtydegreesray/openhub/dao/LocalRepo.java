package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table LOCAL_REPO (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "LOCAL_REPO")
public class LocalRepo {

    @PrimaryKey
    @ColumnInfo(name = "ID")
    private long id;

    @NonNull
    @ColumnInfo(name = "NAME")
    private String name;

    @ColumnInfo(name = "DESCRIPTION")
    private String description;

    @ColumnInfo(name = "LANGUAGE")
    private String language;

    @ColumnInfo(name = "STARGAZERS_COUNT")
    private Integer stargazersCount;

    @ColumnInfo(name = "WATCHERS_COUNT")
    private Integer watchersCount;

    @ColumnInfo(name = "FORKS_COUNT")
    private Integer forksCount;

    @ColumnInfo(name = "FORK")
    private Boolean fork;

    @ColumnInfo(name = "OWNER_LOGIN")
    private String ownerLogin;

    @ColumnInfo(name = "OWNER_AVATAR_URL")
    private String ownerAvatarUrl;

    public LocalRepo() {
    }

    @Ignore
    public LocalRepo(long id) {
        this.id = id;
    }

    @Ignore
    public LocalRepo(long id, String name, String description, String language, Integer stargazersCount, Integer watchersCount, Integer forksCount, Boolean fork, String ownerLogin, String ownerAvatarUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.language = language;
        this.stargazersCount = stargazersCount;
        this.watchersCount = watchersCount;
        this.forksCount = forksCount;
        this.fork = fork;
        this.ownerLogin = ownerLogin;
        this.ownerAvatarUrl = ownerAvatarUrl;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getStargazersCount() {
        return stargazersCount;
    }

    public void setStargazersCount(Integer stargazersCount) {
        this.stargazersCount = stargazersCount;
    }

    public Integer getWatchersCount() {
        return watchersCount;
    }

    public void setWatchersCount(Integer watchersCount) {
        this.watchersCount = watchersCount;
    }

    public Integer getForksCount() {
        return forksCount;
    }

    public void setForksCount(Integer forksCount) {
        this.forksCount = forksCount;
    }

    public Boolean getFork() {
        return fork;
    }

    public void setFork(Boolean fork) {
        this.fork = fork;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    public String getOwnerAvatarUrl() {
        return ownerAvatarUrl;
    }

    public void setOwnerAvatarUrl(String ownerAvatarUrl) {
        this.ownerAvatarUrl = ownerAvatarUrl;
    }

}
