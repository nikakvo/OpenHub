package com.thirtydegreesray.openhub.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** History of opened repositories and users. */
@Dao
public interface TraceDao {

    /** One page, most recently opened first. */
    @Query("SELECT * FROM TRACE ORDER BY LATEST_TIME DESC LIMIT :limit OFFSET :offset")
    List<Trace> page(int offset, int limit);

    @Query("SELECT * FROM TRACE WHERE REPO_ID = :repoId LIMIT 1")
    Trace findByRepo(long repoId);

    @Query("SELECT * FROM TRACE WHERE USER_ID = :login LIMIT 1")
    Trace findByUser(String login);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Trace trace);

    @Update
    void update(Trace trace);

    @Query("DELETE FROM TRACE WHERE ID = :id")
    void deleteById(String id);
}
