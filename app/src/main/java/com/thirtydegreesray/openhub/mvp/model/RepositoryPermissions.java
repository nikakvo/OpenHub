

package com.thirtydegreesray.openhub.mvp.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created on 2017/7/19.
 *
 * @author ThirtyDegreesRay
 */

public class RepositoryPermissions implements Parcelable {

    private boolean admin;
    private boolean push;
    private boolean pull;
    private boolean triage;
    private boolean maintain;

    public RepositoryPermissions() {

    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isPush() {
        return push;
    }

    public void setPush(boolean push) {
        this.push = push;
    }

    public boolean isPull() {
        return pull;
    }

    public void setPull(boolean pull) {
        this.pull = pull;
    }

    /** Triage role or higher: may label, close, archive and unarchive pull requests. */
    public boolean canTriage() {
        return triage || push || maintain || admin;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.admin ? (byte) 1 : (byte) 0);
        dest.writeByte(this.push ? (byte) 1 : (byte) 0);
        dest.writeByte(this.pull ? (byte) 1 : (byte) 0);
        dest.writeByte(this.triage ? (byte) 1 : (byte) 0);
        dest.writeByte(this.maintain ? (byte) 1 : (byte) 0);
    }

    protected RepositoryPermissions(Parcel in) {
        this.admin = in.readByte() != 0;
        this.push = in.readByte() != 0;
        this.pull = in.readByte() != 0;
        this.triage = in.readByte() != 0;
        this.maintain = in.readByte() != 0;
    }

    public static final Parcelable.Creator<RepositoryPermissions> CREATOR = new Parcelable.Creator<RepositoryPermissions>() {
        @Override
        public RepositoryPermissions createFromParcel(Parcel source) {
            return new RepositoryPermissions(source);
        }

        @Override
        public RepositoryPermissions[] newArray(int size) {
            return new RepositoryPermissions[size];
        }
    };
}
