package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * {@link TlsGuard} stopped a request: the connection to a GitHub host is not GitHub's own.
 * The message is short and readable, because many screens show it as it is.
 */
public final class InterceptedException extends IOException {

    @NonNull public final String host;
    /** Why the check failed (for logs and tests, never shown to the user). */
    @NonNull public final TlsChainCheck.Result result;

    public InterceptedException(@NonNull String host, @NonNull TlsChainCheck.Result result) {
        super("Connection to " + host + " intercepted: request not sent");
        this.host = host;
        this.result = result;
    }
}
