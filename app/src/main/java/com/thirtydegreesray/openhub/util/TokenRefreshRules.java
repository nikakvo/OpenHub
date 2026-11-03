package com.thirtydegreesray.openhub.util;

import androidx.annotation.Nullable;

/**
 * The decisions of the token refresh, as plain Java (unit tested). The work itself is in
 * {@link TokenRefresher}.
 */
public final class TokenRefreshRules {

    private TokenRefreshRules() {
    }

    /** A token that ends within this time is refreshed before it is used. */
    public static final long REFRESH_MARGIN_MS = 5 * 60 * 1000L;

    /** After a refresh failed for a temporary reason, the next try waits this long. */
    public static final long RETRY_PAUSE_MS = 30 * 1000L;

    /** Request bodies up to this size are sent again after a refresh. */
    public static final long MAX_REPLAY_BYTES = 1024 * 1024L;

    /** What a refresh answer means. */
    public enum Outcome {
        /** A new token pair: store it and use it. */
        OK,
        /** GitHub refused the refresh token for good: the user has to sign in again. */
        REFUSED,
        /** No answer, a server error or anything unclear: change nothing, try later. */
        TEMPORARY
    }

    /** True when the access token should be refreshed before it is used. */
    public static boolean refreshSoon(@Nullable Long accessExpiresAt, long nowMs) {
        return accessExpiresAt != null && nowMs >= accessExpiresAt - REFRESH_MARGIN_MS;
    }

    /**
     * The meaning of GitHub's answer. Only an explicit "bad_refresh_token" (wrong, used,
     * expired or revoked refresh token) asks the user to sign in again; everything else,
     * including errors about the app itself, is treated as temporary.
     */
    public static Outcome outcome(int httpCode, @Nullable String accessToken,
                                  @Nullable String error) {
        if (httpCode == 200 && accessToken != null && !accessToken.trim().isEmpty()
                && (error == null || error.isEmpty())) {
            return Outcome.OK;
        }
        if ("bad_refresh_token".equals(error)) return Outcome.REFUSED;
        return Outcome.TEMPORARY;
    }

    /** False while the pause after a temporary failure lasts. */
    public static boolean mayTry(@Nullable Long lastFailureMs, long nowMs) {
        return lastFailureMs == null || nowMs - lastFailureMs >= RETRY_PAUSE_MS
                || nowMs < lastFailureMs; // the clock was set back
    }

    /** The token of an Authorization header ("token X" or "Bearer X"); null for others. */
    @Nullable
    public static String tokenOf(@Nullable String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        String token;
        if (value.regionMatches(true, 0, "token ", 0, 6)) {
            token = value.substring(6).trim();
        } else if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = value.substring(7).trim();
        } else {
            return null; // Basic and anything else is not an account token
        }
        return token.isEmpty() ? null : token;
    }

    /** True when a request can be sent once more with the new token. */
    public static boolean canReplay(boolean hasBody, boolean oneShot, long contentLength) {
        if (!hasBody) return true;
        return !oneShot && contentLength >= 0 && contentLength <= MAX_REPLAY_BYTES;
    }
}
