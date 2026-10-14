package com.thirtydegreesray.openhub.util;

/**
 * Tells the Bookmarks and History screens that their data changed on another screen
 * (a repository or user was bookmarked, unbookmarked or opened), so they reload when they
 * come back to the front instead of waiting for a pull to refresh.
 */
public final class LocalChanges {

    private static volatile long bookmarks = 1;
    private static volatile long history = 1;

    private LocalChanges() {
    }

    public static void markBookmarks() {
        bookmarks++;
    }

    public static void markHistory() {
        history++;
    }

    /** Current value; remember it when loading and compare with {@link #changedSince}. */
    public static long bookmarksStamp() {
        return bookmarks;
    }

    public static long historyStamp() {
        return history;
    }

    public static boolean changedSince(long seen, long now) {
        return seen != 0 && seen != now;
    }
}
