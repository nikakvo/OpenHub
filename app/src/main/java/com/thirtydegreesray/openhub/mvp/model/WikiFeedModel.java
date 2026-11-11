package com.thirtydegreesray.openhub.mvp.model;

import java.util.ArrayList;

/**
 * Created by ThirtyDegreesRay on 2017/12/7 10:01:39
 */
public class WikiFeedModel {

    private String id;
    private String title;
    private String updated;
    private ArrayList<WikiModel> wikiList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public ArrayList<WikiModel> getWikiList() {
        return wikiList;
    }

    public void setWikiList(ArrayList<WikiModel> wikiList) {
        this.wikiList = wikiList;
    }

}
