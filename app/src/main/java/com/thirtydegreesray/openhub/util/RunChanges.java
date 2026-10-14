package com.thirtydegreesray.openhub.util;

/**
 * Tells the workflow runs list that a run was started, re-run or cancelled on another
 * screen, so it reloads when it comes back to the front.
 */
public final class RunChanges {

    private static volatile long stamp = 1;

    private RunChanges() {
    }

    public static void markChanged() {
        stamp++;
    }

    public static long stamp() {
        return stamp;
    }
}
