package com.thirtydegreesray.openhub.mvp.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * A local file picked for upload as a release asset: its content Uri, the asset name
 * it gets on GitHub (renamable before the upload) and its size, -1 when unknown.
 */
public class UploadItem implements Parcelable {

    private final String uri;
    private String name;
    private final long size;

    public UploadItem(@NonNull String uri, @NonNull String name, long size) {
        this.uri = uri;
        this.name = name;
        this.size = size;
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri);
        dest.writeString(name);
        dest.writeLong(size);
    }

    protected UploadItem(Parcel in) {
        uri = in.readString();
        name = in.readString();
        size = in.readLong();
    }

    public static final Creator<UploadItem> CREATOR = new Creator<UploadItem>() {
        @Override
        public UploadItem createFromParcel(Parcel source) {
            return new UploadItem(source);
        }

        @Override
        public UploadItem[] newArray(int size) {
            return new UploadItem[size];
        }
    };
}
