package com.thirtydegreesray.openhub.util;

/**
 * Raised when a workflow is enabled or disabled in the app, so the Actions screen loads
 * the workflow list again (its picker shows which workflows are disabled).
 */
public final class WorkflowChanges {

    private static volatile long stamp = 1;

    private WorkflowChanges() {
    }

    public static void markChanged() {
        stamp++;
    }

    public static long stamp() {
        return stamp;
    }
}
