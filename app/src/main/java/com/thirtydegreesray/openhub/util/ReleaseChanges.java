package com.thirtydegreesray.openhub.util;

/**
 * Tells release screens that release data changed somewhere else (edited, deleted,
 * published, an upload finished), so they reload when they come back to the front
 * instead of showing a stale copy.
 */
public final class ReleaseChanges {

    private static volatile long stamp = 1;

    private ReleaseChanges() {
    }

    public static void markChanged() {
        stamp++;
    }

    /** Current value; remember it after loading and compare with {@link #changedSince}. */
    public static long stamp() {
        return stamp;
    }

    public static boolean changedSince(long seen) {
        return seen != 0 && seen != stamp;
    }
}
