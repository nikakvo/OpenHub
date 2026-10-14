package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Row of the table MY_TRENDING_LANGUAGE (Room entity). Table and column names are the ones
 * greenDAO used, so the database of older versions keeps working.
 */
@Entity(tableName = "MY_TRENDING_LANGUAGE")
public class MyTrendingLanguage {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "SLUG")
    private String slug;

    @NonNull
    @ColumnInfo(name = "NAME")
    private String name;

    @ColumnInfo(name = "ORDER")
    private int order;

    public MyTrendingLanguage() {
    }

    @Ignore
    public MyTrendingLanguage(String slug) {
        this.slug = slug;
    }

    @Ignore
    public MyTrendingLanguage(String slug, String name, int order) {
        this.slug = slug;
        this.name = name;
        this.order = order;
    }

    @NonNull
    public String getSlug() {
        return slug;
    }

    public void setSlug(@NonNull String slug) {
        this.slug = slug;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

}
