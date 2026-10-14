package com.thirtydegreesray.openhub.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** Local copies of repositories shown in bookmarks and history. */
@Dao
public interface LocalRepoDao {

    @Query("SELECT * FROM LOCAL_REPO WHERE ID = :id")
    LocalRepo load(long id);

    /** Inserts or replaces the copy. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(LocalRepo repo);
}
