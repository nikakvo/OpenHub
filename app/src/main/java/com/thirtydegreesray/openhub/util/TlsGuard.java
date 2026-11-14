package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.BuildConfig;

import java.io.IOException;
import java.net.Socket;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import okhttp3.Connection;
import okhttp3.Handshake;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Network interceptor for every OkHttp client that talks to GitHub with the sign-in:
 * before a request leaves the phone, the certificates of the connection must lead to one
 * of GitHub's certificate authorities ({@link TlsChainCheck}, {@link TlsAnchors}).
 *
 * Android already refuses certificates it does not trust. This second check is for a phone
 * where someone switched Android's checks off inside OpenHub (an SSL unpinning module) or
 * added his own certificate to the system: the request is then not sent (the sign-in never
 * travels over such a connection) and the screen in front says why. Nothing crashes and
 * nothing is deleted: without the interception everything works again at once.
 *
 * The request then fails with an {@link InterceptedException}.
 *
 * Debug builds skip it, so a developer can look at the app's traffic with a proxy.
 */
public final class TlsGuard implements Interceptor {

    public static final TlsGuard INSTANCE = new TlsGuard();

    /** Sessions already found GitHub's own, with their host (a connection carries many requests). */
    private final Map<SSLSession, String> checked =
            Collections.synchronizedMap(new WeakHashMap<>());
    @Nullable private volatile List<TlsChainCheck.Key> keys;

    private TlsGuard() {
    }

    /** The hosts that receive the sign-in (API, uploads, the OAuth sign-in on github.com). */
    public static boolean guards(@Nullable String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("api.github.com") || h.equals("uploads.github.com") || h.equals("github.com");
    }

    /** False in debug builds only. */
    public static boolean isActive() {
        return !BuildConfig.DEBUG;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();
        if (!request.isHttps() || !guards(host) || !isActive()) return chain.proceed(request);

        TlsChainCheck.Result result = check(chain.connection(), host);
        if (result != TlsChainCheck.Result.OK) {
            InterceptionNotice.report(host);
            throw new InterceptedException(host, result);
        }
        return chain.proceed(request);
    }

    /** Checks one connection (also used by About's connection test). */
    @NonNull
    public TlsChainCheck.Result check(@Nullable Connection connection, @NonNull String host) {
        if (connection == null) return TlsChainCheck.Result.NO_CERTIFICATES;
        // the certificates as the TLS session received them, not a list cleaned by a
        // trust manager (which a module may have replaced)
        Certificate[] sent = null;
        SSLSession session = null;
        Socket socket = connection.socket();
        if (socket instanceof SSLSocket) {
            session = ((SSLSocket) socket).getSession();
            if (session != null) {
                if (host.equalsIgnoreCase(checked.get(session))) return TlsChainCheck.Result.OK;
                try {
                    sent = session.getPeerCertificates();
                } catch (Exception ignored) {
                    // no certificates: refused below
                }
            }
        }
        if (sent == null) {
            Handshake handshake = connection.handshake();
            if (handshake != null) sent = handshake.peerCertificates().toArray(new Certificate[0]);
        }
        List<TlsChainCheck.Key> anchors = keys;
        if (anchors == null) {
            anchors = TlsChainCheck.keys(TlsAnchors.ALL);
            keys = anchors;
        }
        TlsChainCheck.Result result = TlsChainCheck.check(host, sent, anchors,
                System.currentTimeMillis());
        if (session != null && result == TlsChainCheck.Result.OK) checked.put(session, host);
        return result;
    }
}
