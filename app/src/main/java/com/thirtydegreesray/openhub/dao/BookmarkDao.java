package com.thirtydegreesray.openhub.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Bookmarked repositories and users. */
@Dao
public interface BookmarkDao {

    /** One page, newest first. */
    @Query("SELECT * FROM BOOKMARK ORDER BY MARK_TIME DESC LIMIT :limit OFFSET :offset")
    List<Bookmark> page(int offset, int limit);

    @Query("SELECT * FROM BOOKMARK WHERE REPO_ID = :repoId LIMIT 1")
    Bookmark findByRepo(long repoId);

    @Query("SELECT * FROM BOOKMARK WHERE USER_ID = :login LIMIT 1")
    Bookmark findByUser(String login);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Bookmark bookmark);

    @Delete
    void delete(Bookmark bookmark);

    @Query("DELETE FROM BOOKMARK WHERE ID = :id")
    void deleteById(String id);
}
