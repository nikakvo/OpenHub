package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.R;

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * The text a screen shows for a failed request. Certificate problems get a short sentence
 * instead of Java's exception text ("java.security.cert.CertPathValidatorException: Trust
 * anchor for certification path not found"), which HyperOS cuts after two lines anyway.
 */
public final class ErrorTexts {

    /** What kind of failure a request had. */
    public enum Kind {
        /** OpenHub's own check stopped it ({@link TlsGuard}). */
        INTERCEPTED,
        /** Android refused the certificate (a proxy, a pin, or a wrong date on the phone). */
        CERTIFICATE_REFUSED,
        OTHER
    }

    private ErrorTexts() {
    }

    /** Pure: looks through the causes, at most 10 deep. */
    @NonNull
    public static Kind kind(@Nullable Throwable error) {
        Throwable e = error;
        for (int i = 0; e != null && i < 10; i++) {
            if (e instanceof InterceptedException) return Kind.INTERCEPTED;
            if (e instanceof SSLHandshakeException || e instanceof SSLPeerUnverifiedException
                    || e instanceof CertPathValidatorException
                    || e instanceof CertificateException) {
                return Kind.CERTIFICATE_REFUSED;
            }
            if (e.getCause() == e) break;
            e = e.getCause();
        }
        return Kind.OTHER;
    }

    /** The text to show: a short sentence for certificate problems, else the error's message. */
    @NonNull
    public static String of(@NonNull Throwable error) {
        Kind kind = kind(error);
        if (kind != Kind.OTHER) {
            try {
                return AppApplication.get().getString(kind == Kind.INTERCEPTED
                        ? R.string.tls_intercepted_toast : R.string.tls_refused_toast);
            } catch (Exception ignored) {
                // no resources (tests): the plain text below
            }
        }
        return StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
    }
}
