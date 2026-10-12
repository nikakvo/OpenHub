package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

/** One entry of GET /licenses: key for the API ("mit"), name for people ("MIT License"). */
public class LicenseTemplate {

    private String key;
    private String name;
    @SerializedName("spdx_id") private String spdxId;

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getSpdxId() {
        return spdxId;
    }
}
