package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class WebhooksTest {

    @Test
    public void events() {
        assertNull(Webhooks.events(null));
        assertNull(Webhooks.events(Collections.emptyList()));
        assertEquals("*", Webhooks.events(Arrays.asList("push", "*")));
        assertEquals("push, pull_request", Webhooks.events(Arrays.asList("push", "pull_request")));
    }

    @Test
    public void lastResponse() {
        assertEquals(Webhooks.Result.NONE, Webhooks.lastResponse(null, "unused"));
        assertEquals(Webhooks.Result.NONE, Webhooks.lastResponse(null, null));
        assertEquals(Webhooks.Result.OK, Webhooks.lastResponse(200, "active"));
        assertEquals(Webhooks.Result.OK, Webhooks.lastResponse(204, "active"));
        assertEquals(Webhooks.Result.FAILED, Webhooks.lastResponse(404, "active"));
        assertEquals(Webhooks.Result.FAILED, Webhooks.lastResponse(null, "timeout"));
        assertEquals(Webhooks.Result.FAILED, Webhooks.lastResponse(502, "failed"));
    }

    @Test
    public void deliveries() {
        assertEquals(Webhooks.Result.OK, Webhooks.delivery(200));
        assertEquals(Webhooks.Result.FAILED, Webhooks.delivery(0));
        assertEquals(Webhooks.Result.FAILED, Webhooks.delivery(500));
        assertEquals("pull_request.opened", Webhooks.deliveryTitle("pull_request", "opened"));
        assertEquals("push", Webhooks.deliveryTitle("push", null));
        assertEquals("ping", Webhooks.deliveryTitle("ping", ""));
        assertEquals("?", Webhooks.deliveryTitle(null, null));
    }

    @Test
    public void durations() {
        assertEquals("0 s", Webhooks.duration(0));
        assertEquals("0.42 s", Webhooks.duration(0.4213));
        assertEquals("9.99 s", Webhooks.duration(9.99));
        assertEquals("12 s", Webhooks.duration(12.4));
    }

    @Test
    public void hostAndCut() {
        assertEquals("example.com", Webhooks.host("https://example.com/hook?x=1"));
        assertEquals("not a url", Webhooks.host("not a url"));
        assertEquals("", Webhooks.host(null));
        assertEquals("abc", Webhooks.cut("abc", 5));
        String cut = Webhooks.cut("abcdefgh", 3);
        assertTrue(cut.startsWith("abc\n"));
        assertTrue(cut.contains("5 more characters"));
        assertEquals("https://github.com/o/r/settings/hooks/7", Webhooks.settingsUrl("o", "r", 7));
        assertEquals("https://github.com/o/r/settings/hooks", Webhooks.settingsUrl("o", "r", 0));
    }
}
