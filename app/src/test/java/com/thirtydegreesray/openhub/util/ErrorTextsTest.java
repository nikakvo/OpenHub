package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.cert.CertPathValidatorException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class ErrorTextsTest {

    @Test
    public void ownCheckIsIntercepted() {
        IOException e = new InterceptedException("api.github.com",
                TlsChainCheck.Result.UNKNOWN_ROOT);
        assertEquals(ErrorTexts.Kind.INTERCEPTED, ErrorTexts.kind(e));
        // Rx and Retrofit wrap it
        assertEquals(ErrorTexts.Kind.INTERCEPTED, ErrorTexts.kind(new RuntimeException(e)));
    }

    @Test
    public void androidRefusalIsCertificateRefused() {
        SSLHandshakeException handshake = new SSLHandshakeException(
                "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.");
        handshake.initCause(new CertPathValidatorException("Trust anchor for certification path not found."));
        assertEquals(ErrorTexts.Kind.CERTIFICATE_REFUSED, ErrorTexts.kind(handshake));
        assertEquals(ErrorTexts.Kind.CERTIFICATE_REFUSED,
                ErrorTexts.kind(new SSLPeerUnverifiedException("Pin verification failed")));
        assertEquals(ErrorTexts.Kind.CERTIFICATE_REFUSED,
                ErrorTexts.kind(new IOException(new CertPathValidatorException("x"))));
    }

    @Test
    public void otherErrorsKeepTheirText() {
        assertEquals(ErrorTexts.Kind.OTHER, ErrorTexts.kind(new SocketTimeoutException("timeout")));
        assertEquals(ErrorTexts.Kind.OTHER, ErrorTexts.kind(null));
        assertEquals("HTTP 404: Not Found", ErrorTexts.of(new IOException("HTTP 404: Not Found")));
        assertEquals("java.io.IOException", ErrorTexts.of(new IOException()));
    }

    @Test
    public void messageHasNoInternalNames() {
        String text = new InterceptedException("api.github.com",
                TlsChainCheck.Result.UNKNOWN_ROOT).getMessage();
        assertEquals("Connection to api.github.com intercepted: request not sent", text);
    }
}
