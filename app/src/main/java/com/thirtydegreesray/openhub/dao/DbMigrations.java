package com.thirtydegreesray.openhub.dao;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Database migrations. Never destructive: accounts, bookmarks, history and languages are
 * always kept. If a migration fails, the whole upgrade is rolled back and the old database
 * stays as it was.
 */
public final class DbMigrations {

    private DbMigrations() {
    }

    /** Suffix of the temporary table while a table is rebuilt. */
    static final String TEMP_SUFFIX = "_room_new";

    /**
     * greenDAO (5) to Room (6): every table is rebuilt with Room's exact SQL
     * ({@link DbSchema}) and its rows are copied over. Table and column names stay.
     */
    public static final Migration MIGRATION_5_6 =
            new Migration(DbSchema.GREENDAO_VERSION, DbSchema.VERSION) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    rebuildTables(db);
                }
            };

    public static final Migration[] ALL = {MIGRATION_5_6};

    static void rebuildTables(@NonNull SupportSQLiteDatabase db) {
        for (String legacy : DbSchema.LEGACY_TABLES) {
            db.execSQL("DROP TABLE IF EXISTS `" + legacy + "`");
        }
        for (DbSchema.Table table : DbSchema.TABLES) {
            if (!tableExists(db, table.name)) {
                db.execSQL(table.createSql(table.name));
                continue;
            }
            String temp = table.name + TEMP_SUFFIX;
            String columns = table.columnList();
            db.execSQL("DROP TABLE IF EXISTS `" + temp + "`");
            db.execSQL(table.createSql(temp));
            db.execSQL("INSERT INTO `" + temp + "` (" + columns + ") SELECT " + columns
                    + " FROM `" + table.name + "`");
            db.execSQL("DROP TABLE `" + table.name + "`");
            db.execSQL("ALTER TABLE `" + temp + "` RENAME TO `" + table.name + "`");
        }
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String name) {
        try (Cursor cursor = db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                new Object[]{name})) {
            return cursor.moveToFirst();
        }
    }
}
