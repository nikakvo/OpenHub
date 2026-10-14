package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppUpdaterTest {

    @Test
    public void releasesAreNewerThanTheirTestBuilds() {
        assertTrue(AppUpdater.compare("3.5.0", "3.4.1") > 0);
        assertTrue(AppUpdater.compare("3.4.1", "3.4.1-tears.9") > 0);
        assertTrue(AppUpdater.compare("3.5.0-tears.10", "3.5.0-tears.9") > 0);
        assertTrue(AppUpdater.compare("3.10.0", "3.9.9") > 0);
        assertEquals(0, AppUpdater.compare("3.5", "3.5.0"));
        assertTrue(AppUpdater.compare("3.4.2-tears.1", "3.5.0") < 0);
    }
}
