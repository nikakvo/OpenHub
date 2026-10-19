package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

public class MilestoneDatesTest {

    private static Date utc(String text) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(text);
    }

    @Test
    public void dueOnAndBack() throws Exception {
        assertEquals("2026-10-30T12:00:00Z", MilestoneDates.dueOn(2026, 9, 30));
        assertEquals("2027-01-05T12:00:00Z", MilestoneDates.dueOn(2027, 0, 5));
        assertArrayEquals(new int[]{2026, 9, 30}, MilestoneDates.day(utc("2026-10-30T07:00:00Z")));
        assertEquals("Oct 30, 2026", MilestoneDates.format(utc("2026-10-30T07:00:00Z")));
    }

    @Test
    public void pastDue() throws Exception {
        TimeZone sofia = TimeZone.getTimeZone("Europe/Sofia");
        Date due = utc("2026-10-19T07:00:00Z");
        assertEquals(0, MilestoneDates.daysPastDue(due, utc("2026-10-19T20:00:00Z"), sofia));
        assertEquals(1, MilestoneDates.daysPastDue(due, utc("2026-10-20T08:00:00Z"), sofia));
        assertEquals(0, MilestoneDates.daysPastDue(due, utc("2026-10-01T08:00:00Z"), sofia));
        assertEquals(12, MilestoneDates.daysPastDue(due, utc("2026-10-31T08:00:00Z"), sofia));
    }

    @Test
    public void percent() {
        assertEquals(0, MilestoneDates.percent(0, 0));
        assertEquals(33, MilestoneDates.percent(2, 1));
        assertEquals(100, MilestoneDates.percent(0, 4));
    }
}
