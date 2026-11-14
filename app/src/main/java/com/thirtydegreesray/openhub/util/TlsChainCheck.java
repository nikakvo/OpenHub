package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks the certificates a server really sent against the known root keys, on its own:
 * it does not use Android's or OkHttp's certificate checks, so a module that switches those
 * off in the app does not switch this one off too.
 *
 * Accepted only when: the first certificate is valid now and names the host, every
 * certificate is signed by the next one, and the chain reaches one of the anchor keys
 * (a certificate signed by an anchor key, or an anchor itself). Pure Java, no Android.
 */
public final class TlsChainCheck {

    public enum Result {
        /** The chain leads to a known root key. */
        OK,
        /** The server sent no certificates (or they could not be read). */
        NO_CERTIFICATES,
        /** The first certificate does not name the host. */
        WRONG_HOST,
        /** A certificate of the chain is not valid at this time. */
        NOT_VALID_NOW,
        /** A certificate is not signed by the next one. */
        BROKEN_CHAIN,
        /** The chain does not lead to any known root: someone else made it. */
        UNKNOWN_ROOT
    }

    /** A root as a usable key, with the SHA-256 of its SubjectPublicKeyInfo. */
    public static final class Key {
        @NonNull final PublicKey key;
        @NonNull final String spkiSha256;

        Key(@NonNull PublicKey key, @NonNull String spkiSha256) {
            this.key = key;
            this.spkiSha256 = spkiSha256;
        }
    }

    private TlsChainCheck() {
    }

    /** The anchors as keys; anchors that cannot be read are left out (never all of them). */
    @NonNull
    public static List<Key> keys(@NonNull List<TlsAnchors.Anchor> anchors) {
        List<Key> keys = new ArrayList<>();
        for (TlsAnchors.Anchor anchor : anchors) {
            try {
                byte[] der = Base64.getDecoder().decode(anchor.spki);
                PublicKey key = KeyFactory.getInstance(anchor.algorithm)
                        .generatePublic(new X509EncodedKeySpec(der));
                keys.add(new Key(key, sha256(der)));
            } catch (Exception ignored) {
                // a provider without this key type: the other roots still work
            }
        }
        return keys;
    }

    @NonNull
    public static Result check(@NonNull String host, @Nullable Certificate[] sent,
                               @NonNull List<Key> anchors, long nowMs) {
        List<X509Certificate> chain = new ArrayList<>();
        if (sent != null) {
            for (Certificate certificate : sent) {
                if (certificate instanceof X509Certificate) chain.add((X509Certificate) certificate);
            }
        }
        if (chain.isEmpty()) return Result.NO_CERTIFICATES;
        Set<String> anchorPins = new HashSet<>();
        for (Key anchor : anchors) anchorPins.add(anchor.spkiSha256);

        Date now = new Date(nowMs);
        X509Certificate leaf = chain.get(0);
        if (!namesHost(leaf, host)) return Result.WRONG_HOST;
        for (int i = 0; i < chain.size(); i++) {
            X509Certificate cert = chain.get(i);
            // a root the server sent along ends the chain
            if (i > 0 && anchorPins.contains(sha256(cert.getPublicKey().getEncoded()))) {
                return Result.OK;
            }
            try {
                cert.checkValidity(now);
            } catch (Exception e) {
                return Result.NOT_VALID_NOW;
            }
            for (Key anchor : anchors) {
                if (signedBy(cert, anchor.key)) return Result.OK;
            }
            if (i + 1 >= chain.size()) return Result.UNKNOWN_ROOT;
            X509Certificate issuer = chain.get(i + 1);
            // the next certificate must be a certificate authority that signed this one
            if (issuer.getBasicConstraints() < 0 || !signedBy(cert, issuer.getPublicKey())) {
                return Result.BROKEN_CHAIN;
            }
        }
        return Result.UNKNOWN_ROOT;
    }

    private static boolean signedBy(@NonNull X509Certificate cert, @NonNull PublicKey key) {
        try {
            cert.verify(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The host is one of the certificate's DNS names (a "*." name covers one label). */
    static boolean namesHost(@NonNull X509Certificate leaf, @NonNull String host) {
        String wanted = host.toLowerCase(Locale.ROOT);
        Collection<List<?>> names;
        try {
            names = leaf.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return false;
        }
        if (names == null) return false;
        for (List<?> entry : names) {
            if (entry.size() < 2 || !(entry.get(0) instanceof Integer)
                    || (Integer) entry.get(0) != 2 || !(entry.get(1) instanceof String)) continue;
            if (hostMatches(((String) entry.get(1)).toLowerCase(Locale.ROOT), wanted)) return true;
        }
        return false;
    }

    static boolean hostMatches(@NonNull String pattern, @NonNull String host) {
        if (pattern.equals(host)) return true;
        if (!pattern.startsWith("*.")) return false;
        String suffix = pattern.substring(1);
        if (!host.endsWith(suffix)) return false;
        String label = host.substring(0, host.length() - suffix.length());
        return !label.isEmpty() && label.indexOf('.') < 0;
    }

    @NonNull
    static String sha256(@NonNull byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return "";
        }
    }
}
