package com.thirtydegreesray.openhub.util;

import androidx.annotation.Nullable;

/**
 * Lifetimes of expiring browser sign-ins. GitHub gives an access token for 8 hours and a
 * refresh token for about 6 months, both as "seconds from now". The app stores the moment
 * they end (epoch ms); null means the token does not expire (personal access tokens and
 * sign-ins made before 3.11.0).
 */
public final class TokenLifetime {

    private TokenLifetime() {
    }

    /** When a token that lives {@code seconds} from {@code nowMs} ends; null if it does not. */
    @Nullable
    public static Long endsAt(long nowMs, @Nullable Long seconds) {
        if (seconds == null || seconds <= 0) return null;
        // a silly value must not overflow into the past
        long capped = Math.min(seconds, 100L * 365 * 24 * 60 * 60);
        return nowMs + capped * 1000L;
    }
}
