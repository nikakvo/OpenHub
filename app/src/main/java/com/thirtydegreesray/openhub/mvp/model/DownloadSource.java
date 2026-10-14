package com.thirtydegreesray.openhub.mvp.model;

/**
 * Created by ThirtyDegreesRay on 2017/9/16 15:55:57
 */

public class DownloadSource {

    private String url;
    private boolean isSourceCode;
    private String name;
    private long size;
    private int downloadCount = -1;
    /** GitHub's checksum ("sha256:..."), null when unknown */
    private String digest;

    public DownloadSource(String url, boolean isSourceCode, String name) {
        this.url = url;
        this.isSourceCode = isSourceCode;
        this.name = name;
    }

    public DownloadSource(String url, boolean isSourceCode, String name, long size) {
        this.url = url;
        this.isSourceCode = isSourceCode;
        this.name = name;
        this.size = size;
    }

    public DownloadSource(String url, String name, long size, int downloadCount) {
        this(url, false, name, size);
        this.downloadCount = downloadCount;
    }

    public String getDigest() {
        return digest;
    }

    public DownloadSource setDigest(String digest) {
        this.digest = digest;
        return this;
    }

    /** -1 for source code archives, which have no counter. */
    public int getDownloadCount() {
        return downloadCount;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSourceCode() {
        return isSourceCode;
    }

    public void setSourceCode(boolean sourceCode) {
        isSourceCode = sourceCode;
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

    public void setSize(long size) {
        this.size = size;
    }
}
