package com.thirtydegreesray.openhub.dao;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table AUTH_USER (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "AUTH_USER")
public class AuthUser implements Parcelable {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "ACCESS_TOKEN")
    private String accessToken;

    @NonNull
    @ColumnInfo(name = "AUTH_TIME")
    private java.util.Date authTime;

    @ColumnInfo(name = "EXPIRE_IN")
    private long expireIn;

    @NonNull
    @ColumnInfo(name = "SCOPE")
    private String scope;

    @ColumnInfo(name = "SELECTED")
    private boolean selected;

    @NonNull
    @ColumnInfo(name = "LOGIN_ID")
    private String loginId;

    @ColumnInfo(name = "NAME")
    private String name;

    @ColumnInfo(name = "AVATAR")
    private String avatar;

    /**
     * Refresh token of an expiring browser sign-in, stored sealed like the access token.
     * Null for tokens that do not expire (personal access tokens, older sign-ins).
     */
    @ColumnInfo(name = "REFRESH_TOKEN")
    private String refreshToken;

    /** When the access token stops working (epoch ms); null = it does not expire. */
    @ColumnInfo(name = "ACCESS_EXPIRES_AT")
    private Long accessExpiresAt;

    /** When the refresh token stops working (epoch ms); null = unknown or none. */
    @ColumnInfo(name = "REFRESH_EXPIRES_AT")
    private Long refreshExpiresAt;

    public AuthUser() {
    }

    @Ignore
    public AuthUser(String accessToken) {
        this.accessToken = accessToken;
    }

    @Ignore
    public AuthUser(String accessToken, java.util.Date authTime, long expireIn, String scope, boolean selected, String loginId, String name, String avatar) {
        this.accessToken = accessToken;
        this.authTime = authTime;
        this.expireIn = expireIn;
        this.scope = scope;
        this.selected = selected;
        this.loginId = loginId;
        this.name = name;
        this.avatar = avatar;
    }

    @NonNull
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(@NonNull String accessToken) {
        this.accessToken = accessToken;
    }

    @NonNull
    public java.util.Date getAuthTime() {
        return authTime;
    }

    public void setAuthTime(@NonNull java.util.Date authTime) {
        this.authTime = authTime;
    }

    public long getExpireIn() {
        return expireIn;
    }

    public void setExpireIn(long expireIn) {
        this.expireIn = expireIn;
    }

    @NonNull
    public String getScope() {
        return scope;
    }

    public void setScope(@NonNull String scope) {
        this.scope = scope;
    }

    public boolean getSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @NonNull
    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(@NonNull String loginId) {
        this.loginId = loginId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(Long accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public Long getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public void setRefreshExpiresAt(Long refreshExpiresAt) {
        this.refreshExpiresAt = refreshExpiresAt;
    }

    /** True when this sign-in has a refresh token (an expiring browser sign-in). */
    public boolean canRefresh() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.accessToken);
        dest.writeLong(this.authTime != null ? this.authTime.getTime() : -1);
        dest.writeLong(this.expireIn);
        dest.writeString(this.scope);
        dest.writeByte(this.selected ? (byte) 1 : (byte) 0);
        dest.writeString(this.loginId);
        dest.writeString(this.name);
        dest.writeString(this.avatar);
        dest.writeString(this.refreshToken);
        writeTime(dest, this.accessExpiresAt);
        writeTime(dest, this.refreshExpiresAt);
    }

    private static void writeTime(Parcel dest, Long time) {
        dest.writeByte(time == null ? (byte) 0 : (byte) 1);
        if (time != null) dest.writeLong(time);
    }

    private static Long readTime(Parcel in) {
        return in.readByte() == 0 ? null : in.readLong();
    }

    @Ignore
    protected AuthUser(Parcel in) {
        this.accessToken = in.readString();
        long tmpAuthTime = in.readLong();
        this.authTime = tmpAuthTime == -1 ? null : new java.util.Date(tmpAuthTime);
        this.expireIn = in.readLong(); // written as a long (was read as an int before)
        this.scope = in.readString();
        this.selected = in.readByte() != 0;
        this.loginId = in.readString();
        this.name = in.readString();
        this.avatar = in.readString();
        this.refreshToken = in.readString();
        this.accessExpiresAt = readTime(in);
        this.refreshExpiresAt = readTime(in);
    }

    public static final Parcelable.Creator<AuthUser> CREATOR = new Parcelable.Creator<AuthUser>() {
        @Override
        public AuthUser createFromParcel(Parcel source) {
            return new AuthUser(source);
        }

        @Override
        public AuthUser[] newArray(int size) {
            return new AuthUser[size];
        }
    };
}
