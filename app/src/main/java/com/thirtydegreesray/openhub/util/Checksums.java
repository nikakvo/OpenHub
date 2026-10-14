package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * SHA-256 checksums of downloads. GitHub computes a digest for every release file
 * ("sha256:" + 64 hex digits); a download whose content does not match it is damaged
 * or was replaced on the way and must not be used.
 */
public final class Checksums {

    private Checksums() {
    }

    /** The 64 lowercase hex digits of a GitHub digest ("sha256:ABC..." or plain), or null. */
    @Nullable
    public static String sha256Of(@Nullable String digest) {
        if (digest == null) return null;
        String value = digest.trim();
        if (value.regionMatches(true, 0, "sha256:", 0, 7)) value = value.substring(7);
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() != 64) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return null;
        }
        return value;
    }

    /** SHA-256 of a stream, 64 lowercase hex digits. Reads the stream to the end. */
    @NonNull
    public static String sha256(@NonNull InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        return hex(digest.digest());
    }

    /** First 12 digits for a compact display. */
    @NonNull
    public static String shortForm(@NonNull String sha256) {
        return sha256.length() > 12 ? sha256.substring(0, 12) : sha256;
    }

    @NonNull
    static String hex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
