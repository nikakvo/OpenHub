package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewCacheTest {

    @Test
    public void emptiedOnlyAboveFiftyMegabytes() {
        assertFalse(WebViewCache.overLimit(0));
        assertFalse(WebViewCache.overLimit(43204L * 1024));
        assertFalse(WebViewCache.overLimit(50L * 1024 * 1024));
        assertTrue(WebViewCache.overLimit(50L * 1024 * 1024 + 1));
    }

    @Test
    public void folderSizeOfMissingFolderIsZero() {
        assertTrue(WebViewCache.folderSize(new java.io.File("/no/such/folder")) == 0);
        assertTrue(WebViewCache.folderSize(null) == 0);
    }
}
