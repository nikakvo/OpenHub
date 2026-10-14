package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;

public class ActionsLogTest {

    private static final String LOG = "﻿2026-10-14T19:43:03.1234567Z ##[group]Run actions/checkout@v4\n"
            + "2026-10-14T19:43:03.1234567Z with:\n"
            + "2026-10-14T19:43:03.1234567Z   repository: nikakvo/test\n"
            + "2026-10-14T19:43:03.2000000Z ##[endgroup]\n"
            + "2026-10-14T19:43:04.0000000Z Syncing repository: nikakvo/test\n"
            + "2026-10-14T19:43:05.0000000Z \u001B[36;1mmake -j8\u001B[0m\n"
            + "2026-10-14T19:43:06.0000000Z ##[warning]Node.js 16 is deprecated\n"
            + "2026-10-14T19:43:07.0000000Z ##[group]Run ./build.sh\n"
            + "2026-10-14T19:43:07.0000000Z ##[command]/usr/bin/bash -e build.sh\n"
            + "2026-10-14T19:43:08.0000000Z ##[error]Process completed with exit code 2.\n"
            + "2026-10-14T19:43:08.0000000Z ##[endgroup]\n"
            + "2026-10-14T19:43:09.0000000Z Post job cleanup.\n";

    private static ActionsLog read(String text, int max) throws Exception {
        return ActionsLog.read(new BufferedReader(new StringReader(text)), max);
    }

    @Test
    public void linesAreCleaned() throws Exception {
        ActionsLog log = read(LOG, 0);
        assertEquals("with:", log.lines.get(0).text);
        assertEquals("make -j8", log.lines.get(3).text);
        assertEquals(ActionsLog.WARNING, log.lines.get(4).kind);
        assertEquals("Node.js 16 is deprecated", log.lines.get(4).text);
        assertEquals(ActionsLog.COMMAND, log.lines.get(5).kind);
        assertEquals(ActionsLog.ERROR, log.lines.get(6).kind);
        assertEquals("Process completed with exit code 2.", log.lines.get(6).text);
        assertEquals("Post job cleanup.", log.lines.get(7).text);
        assertEquals(8, log.lines.size());
    }

    @Test
    public void groups() throws Exception {
        ActionsLog log = read(LOG, 0);
        ActionsLog.Group checkout = log.groups.get(log.lines.get(0).group);
        assertEquals("Run actions/checkout@v4", checkout.title);
        assertEquals(2, checkout.lineCount);
        assertFalse(checkout.hasError);
        ActionsLog.Group build = log.groups.get(log.lines.get(6).group);
        assertEquals("Run ./build.sh", build.title);
        assertTrue(build.hasError);
        assertNull(log.groups.get(log.lines.get(7).group).title);
    }

    @Test
    public void searchAndErrors() throws Exception {
        ActionsLog log = read(LOG, 0);
        assertEquals(3, log.find("MAKE", 0));
        assertEquals(-1, log.find("make", 4));
        assertEquals(6, log.nextError(0));
        assertEquals(-1, log.nextError(7));
    }

    @Test
    public void longLogsKeepTheEnd() throws Exception {
        ActionsLog log = read(LOG, 3);
        assertEquals(3, log.lines.size());
        assertEquals(5, log.skipped);
        assertEquals("Post job cleanup.", log.lines.get(2).text);
        assertEquals(0, log.groups.get(log.lines.get(0).group).firstLine);
    }

    @Test
    public void cleanKeepsLinesWithoutTimestamp() {
        assertEquals("plain", ActionsLog.clean("plain"));
        assertEquals("", ActionsLog.clean("2026-10-14T19:43:03Z"));
    }
}
