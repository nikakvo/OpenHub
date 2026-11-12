package com.thirtydegreesray.openhub.util;

/**
 * When a screen that Android brings back in a new process may sign in again by itself
 * (BaseActivity.restoreAccount). Pure Java, tested in AccountRestoreTest.
 */
public final class AccountRestore {

    private AccountRestore() {
    }

    /**
     * True when the selected account can be used again: it exists, its token can be read,
     * GitHub has not refused it, and it is the login the screen was opened with.
     */
    public static boolean usable(String accountLogin, String accessToken, String savedLogin,
                                 boolean refused) {
        if (accountLogin == null || savedLogin == null || refused) return false;
        if (accessToken == null || accessToken.trim().isEmpty()) return false;
        return accountLogin.equalsIgnoreCase(savedLogin);
    }
}
