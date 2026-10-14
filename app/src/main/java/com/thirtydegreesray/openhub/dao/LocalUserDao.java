package com.thirtydegreesray.openhub.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** Local copies of users shown in bookmarks and history. */
@Dao
public interface LocalUserDao {

    @Query("SELECT * FROM LOCAL_USER WHERE LOGIN = :login")
    LocalUser load(String login);

    /** Inserts or replaces the copy. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(LocalUser user);
}
