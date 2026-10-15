package com.thirtydegreesray.openhub.util;

/**
 * Tells gist lists that a gist was created, edited, deleted, starred or forked on another
 * screen, so they reload when they come back to the front.
 */
public final class GistChanges {

    private static volatile long stamp = 1;

    private GistChanges() {
    }

    public static void markChanged() {
        stamp++;
    }

    public static long stamp() {
        return stamp;
    }
}
