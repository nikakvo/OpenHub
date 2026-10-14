package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * One browser sign-in (OAuth), protected like RFC 6749 / RFC 7636 ask:
 * a random "state" that the callback must bring back unchanged (so another app cannot
 * push its own sign-in into OpenHub), and PKCE: the code can only be exchanged with the
 * secret verifier that never left the phone.
 *
 * Kept in private preferences because the app may be closed while the browser is open.
 * Used once, valid for 10 minutes.
 */
public final class OAuthSession {

    private static final String PREFS = "oauth_session";
    private static final String KEY_STATE = "state";
    private static final String KEY_VERIFIER = "verifier";
    private static final String KEY_STARTED = "started";
    private static final long MAX_AGE_MS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OAuthSession() {
    }

    /** The values of one sign-in. */
    public static final class Start {
        @NonNull public final String state;
        @NonNull public final String codeChallenge;

        Start(@NonNull String state, @NonNull String codeChallenge) {
            this.state = state;
            this.codeChallenge = codeChallenge;
        }
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Starts a sign-in: new state and verifier, saved until the callback comes. */
    @NonNull
    public static Start begin(@NonNull Context context) {
        String state = randomToken(32);
        String verifier = randomToken(48);
        prefs(context).edit()
                .putString(KEY_STATE, state)
                .putString(KEY_VERIFIER, verifier)
                .putLong(KEY_STARTED, System.currentTimeMillis())
                .apply();
        return new Start(state, codeChallenge(verifier));
    }

    /**
     * Checks the state of the callback and hands out the verifier (once).
     *
     * @return the PKCE verifier, or null when the callback does not belong to a sign-in
     *         started here (wrong or missing state, too old, or already used)
     */
    @Nullable
    public static String finish(@NonNull Context context, @Nullable String returnedState) {
        SharedPreferences prefs = prefs(context);
        String state = prefs.getString(KEY_STATE, null);
        String verifier = prefs.getString(KEY_VERIFIER, null);
        long started = prefs.getLong(KEY_STARTED, 0);
        prefs.edit().clear().apply();
        if (state == null || verifier == null || returnedState == null) return null;
        if (System.currentTimeMillis() - started > MAX_AGE_MS) return null;
        boolean same = MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                returnedState.getBytes(StandardCharsets.UTF_8));
        return same ? verifier : null;
    }

    /** URL-safe random text with {@code bytes} random bytes. */
    @NonNull
    static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** BASE64URL(SHA-256(verifier)), the S256 method of RFC 7636. */
    @NonNull
    public static String codeChallenge(@NonNull String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
