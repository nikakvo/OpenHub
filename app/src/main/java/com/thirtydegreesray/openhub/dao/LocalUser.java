package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table LOCAL_USER (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "LOCAL_USER")
public class LocalUser {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "LOGIN")
    private String login;

    @ColumnInfo(name = "NAME")
    private String name;

    @ColumnInfo(name = "AVATAR_URL")
    private String avatarUrl;

    @ColumnInfo(name = "FOLLOWERS")
    private Integer followers;

    @ColumnInfo(name = "FOLLOWING")
    private Integer following;

    public LocalUser() {
    }

    @Ignore
    public LocalUser(String login) {
        this.login = login;
    }

    @Ignore
    public LocalUser(String login, String name, String avatarUrl, Integer followers, Integer following) {
        this.login = login;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.followers = followers;
        this.following = following;
    }

    @NonNull
    public String getLogin() {
        return login;
    }

    public void setLogin(@NonNull String login) {
        this.login = login;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getFollowers() {
        return followers;
    }

    public void setFollowers(Integer followers) {
        this.followers = followers;
    }

    public Integer getFollowing() {
        return following;
    }

    public void setFollowing(Integer following) {
        this.following = following;
    }

}
