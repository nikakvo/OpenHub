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

    /** Deletes the copies that no bookmark and no history entry uses any more. */
    @Query("DELETE FROM LOCAL_REPO WHERE ID NOT IN (SELECT REPO_ID FROM BOOKMARK "
            + "WHERE REPO_ID IS NOT NULL UNION SELECT REPO_ID FROM TRACE WHERE REPO_ID IS NOT NULL)")
    int deleteUnused();
}
