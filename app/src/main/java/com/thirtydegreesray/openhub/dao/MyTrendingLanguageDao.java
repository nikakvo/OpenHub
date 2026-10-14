package com.thirtydegreesray.openhub.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/** The languages chosen for Trending, in their order. */
@Dao
public abstract class MyTrendingLanguageDao {

    @Query("SELECT * FROM MY_TRENDING_LANGUAGE ORDER BY `ORDER` ASC")
    public abstract List<MyTrendingLanguage> loadAll();

    @Query("DELETE FROM MY_TRENDING_LANGUAGE")
    abstract void deleteAll();

    @Insert
    abstract void insertAll(List<MyTrendingLanguage> languages);

    /** Replaces the whole list in one transaction. */
    @Transaction
    public void replaceAll(List<MyTrendingLanguage> languages) {
        deleteAll();
        insertAll(languages);
    }
}
