package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What "Share to OpenHub" accepts: never OpenHub's own private files. */
public class ContentFilesTest {

    private static final String OWN = "io.github.nikakvo.openhub";

    @Test
    public void otherAppsContentIsAccepted() {
        assertTrue(ContentFiles.acceptsShared("content", "com.android.providers.downloads.documents", OWN));
        assertTrue(ContentFiles.acceptsShared("content", "media", OWN));
        assertTrue(ContentFiles.acceptsShared("content", "org.telegram.messenger.provider", OWN));
    }

    @Test
    public void ownDownloadProviderIsAccepted() {
        assertTrue(ContentFiles.acceptsShared("content", OWN + ".fileProvider", OWN));
    }

    @Test
    public void filesAndOwnProvidersAreRefused() {
        assertFalse(ContentFiles.acceptsShared("file", "", OWN));
        assertFalse(ContentFiles.acceptsShared("file", null, OWN));
        assertFalse(ContentFiles.acceptsShared(null, "media", OWN));
        assertFalse(ContentFiles.acceptsShared("android.resource", OWN, OWN));
        assertFalse(ContentFiles.acceptsShared("content", OWN, OWN));
        assertFalse(ContentFiles.acceptsShared("content", OWN + ".androidx-startup", OWN));
        assertFalse(ContentFiles.acceptsShared("content", "media;" + OWN + ".other", OWN));
        assertFalse(ContentFiles.acceptsShared("content", "", OWN));
    }

    @Test
    public void assetNames() {
        assertEquals("app_release.apk", ContentFiles.assetName("app/release.apk"));
        assertEquals("a_b_c", ContentFiles.assetName(" a:b*c "));
        assertEquals("file", ContentFiles.assetName("   "));
    }
}
