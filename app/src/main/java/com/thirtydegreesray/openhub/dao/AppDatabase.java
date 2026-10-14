package com.thirtydegreesray.openhub.dao;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.thirtydegreesray.openhub.AppConfig;

/**
 * The app database (Room). Same file as the old greenDAO database (OpenHub.db); the
 * migration in {@link DbMigrations} takes it over without losing data.
 */
@Database(
        entities = {
                AuthUser.class,
                LocalUser.class,
                LocalRepo.class,
                Trace.class,
                Bookmark.class,
                MyTrendingLanguage.class
        },
        version = DbSchema.VERSION,
        exportSchema = true)
@TypeConverters(DbConverters.class)
public abstract class AppDatabase extends RoomDatabase {

    @NonNull
    public static AppDatabase open(@NonNull Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                        AppConfig.DB_NAME)
                .addMigrations(DbMigrations.ALL)
                // the tables are tiny; the screens have always read them on the main thread
                .allowMainThreadQueries()
                .build();
    }
}
