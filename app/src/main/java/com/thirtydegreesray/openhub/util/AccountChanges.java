package com.thirtydegreesray.openhub.util;

/**
 * Tells the repository lists that the signed-in account changed something on another
 * screen (a repository was created, forked, renamed, transferred, deleted or changed in
 * Settings; a repository was starred or unstarred), so "My repositories" and "Starred"
 * reload when they come back to the front instead of waiting for a pull to refresh.
 */
public final class AccountChanges {

    private static volatile long repos = 1;
    private static volatile long stars = 1;

    private AccountChanges() {
    }

    public static void markRepos() {
        repos++;
    }

    public static void markStars() {
        stars++;
    }

    public static long reposStamp() {
        return repos;
    }

    public static long starsStamp() {
        return stars;
    }
}
