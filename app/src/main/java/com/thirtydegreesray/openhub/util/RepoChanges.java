package com.thirtydegreesray.openhub.util;

/**
 * Tells file screens that a commit changed the repository on another screen, so the
 * file list reloads when it comes back instead of showing the old files.
 */
public final class RepoChanges {

    private static volatile long stamp = 1;

    private RepoChanges() {
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
