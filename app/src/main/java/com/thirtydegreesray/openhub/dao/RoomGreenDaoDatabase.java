package com.thirtydegreesray.openhub.dao;

import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

/**
 * Temporary bridge: lets the old greenDAO DAOs run on the connection that Room opened, so
 * there is only one owner of the database file while the screens move to Room. Removed
 * together with greenDAO.
 */
public class RoomGreenDaoDatabase implements Database {

    private final SupportSQLiteDatabase db;

    public RoomGreenDaoDatabase(@NonNull SupportSQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return selectionArgs == null ? db.query(sql) : db.query(sql, selectionArgs);
    }

    @Override
    public void execSQL(String sql) throws SQLException {
        db.execSQL(sql);
    }

    @Override
    public void beginTransaction() {
        db.beginTransaction();
    }

    @Override
    public void endTransaction() {
        db.endTransaction();
    }

    @Override
    public boolean inTransaction() {
        return db.inTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        db.execSQL(sql, bindArgs);
    }

    @Override
    public DatabaseStatement compileStatement(String sql) {
        return new Statement(db.compileStatement(sql));
    }

    @Override
    public boolean isDbLockedByCurrentThread() {
        return db.isDbLockedByCurrentThread();
    }

    @Override
    public boolean isOpen() {
        return db.isOpen();
    }

    @Override
    public void close() {
        // Room owns the connection
    }

    @Override
    public Object getRawDatabase() {
        return db;
    }

    private static class Statement implements DatabaseStatement {

        private final SupportSQLiteStatement statement;

        Statement(SupportSQLiteStatement statement) {
            this.statement = statement;
        }

        @Override
        public void execute() {
            statement.execute();
        }

        @Override
        public long simpleQueryForLong() {
            return statement.simpleQueryForLong();
        }

        @Override
        public void bindNull(int index) {
            statement.bindNull(index);
        }

        @Override
        public long executeInsert() {
            return statement.executeInsert();
        }

        @Override
        public void bindString(int index, String value) {
            statement.bindString(index, value);
        }

        @Override
        public void bindBlob(int index, byte[] value) {
            statement.bindBlob(index, value);
        }

        @Override
        public void bindLong(int index, long value) {
            statement.bindLong(index, value);
        }

        @Override
        public void clearBindings() {
            statement.clearBindings();
        }

        @Override
        public void bindDouble(int index, double value) {
            statement.bindDouble(index, value);
        }

        @Override
        public void close() {
            try {
                statement.close();
            } catch (Exception ignored) {
            }
        }

        @Override
        public Object getRawStatement() {
            return statement;
        }
    }
}
