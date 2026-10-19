package com.thirtydegreesray.openhub.util;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Due dates of milestones. GitHub keeps a day, sent as a time of that day in UTC; it is
 * shown as that day (in UTC, so it never moves to the day before). Pure Java, tested.
 */
public final class MilestoneDates {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private MilestoneDates() {
    }

    /** "2026-10-30T12:00:00Z" for a day picked on the phone (month 0-11). */
    public static String dueOn(int year, int month, int day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02dT12:00:00Z", year, month + 1, day);
    }

    /** {year, month (0-11), day} of a due date. */
    public static int[] day(Date due) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.setTime(due);
        return new int[]{calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)};
    }

    /** "Oct 30, 2026" */
    public static String format(Date due) {
        int[] d = day(due);
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
                "Oct", "Nov", "Dec"};
        return months[d[1]] + " " + d[2] + ", " + d[0];
    }

    /** Whole days the due day lies before today (local day), 0 when not past due. */
    public static int daysPastDue(Date due, Date now, TimeZone local) {
        int[] d = day(due);
        Calendar dueDay = Calendar.getInstance(local);
        dueDay.clear();
        dueDay.set(d[0], d[1], d[2]);
        Calendar today = Calendar.getInstance(local);
        today.setTime(now);
        int y = today.get(Calendar.YEAR);
        int m = today.get(Calendar.MONTH);
        int dd = today.get(Calendar.DAY_OF_MONTH);
        today.clear();
        today.set(y, m, dd);
        long diff = today.getTimeInMillis() - dueDay.getTimeInMillis();
        long days = Math.round(diff / 86_400_000.0);
        return days > 0 ? (int) days : 0;
    }

    /** Percent of closed issues, as GitHub shows it (0 for an empty milestone). */
    public static int percent(int open, int closed) {
        int all = open + closed;
        return all == 0 ? 0 : (int) Math.floor(closed * 100.0 / all);
    }
}
