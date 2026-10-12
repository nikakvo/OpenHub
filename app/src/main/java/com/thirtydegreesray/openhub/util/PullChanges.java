package com.thirtydegreesray.openhub.util;

/**
 * Tells pull request screens that a pull request was created, merged, closed or
 * changed on another screen, so lists reload when they come back.
 */
public final class PullChanges {

    private static volatile long stamp = 1;

    private PullChanges() {
    }

    public static void markChanged() {
        stamp++;
    }

    public static long stamp() {
        return stamp;
    }

    public static boolean changedSince(long seen) {
        return seen != 0 && seen != stamp;
    }
}
