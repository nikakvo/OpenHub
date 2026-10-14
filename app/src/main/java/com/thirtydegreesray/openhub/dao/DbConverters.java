package com.thirtydegreesray.openhub.dao;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Room type converters. Dates are stored as milliseconds (INTEGER), as greenDAO did.
 */
public class DbConverters {

    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date toDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }
}
