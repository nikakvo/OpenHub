package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Public SSH keys for the Deploy keys screen: reads what the user pasted or picked (an
 * OpenSSH ".pub" line), refuses private keys before they leave the phone, and works out
 * the SHA256 fingerprint that github.com and ssh-keygen -l show (GitHub's API does not
 * send it). Pure Java, so it is unit tested.
 */
public final class DeployKeys {

    private DeployKeys() {
    }

    /** Key types GitHub accepts for SSH keys. */
    public static final List<String> TYPES = Arrays.asList(
            "ssh-ed25519", "ssh-rsa",
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521",
            "sk-ssh-ed25519@openssh.com", "sk-ecdsa-sha2-nistp256@openssh.com");

    public enum Problem { NONE, EMPTY, PRIVATE_KEY, NOT_A_KEY, UNKNOWN_TYPE, BROKEN }

    /** A pasted key: either {@link #problem} is set, or type, blob and the line to send are. */
    public static final class Parsed {
        @NonNull public final Problem problem;
        @NonNull public final String type;
        @NonNull public final String base64;
        /** the text after the key, often "user@host"; "" when there is none */
        @NonNull public final String comment;
        @NonNull final byte[] blob;

        Parsed(@NonNull Problem problem, @NonNull String type, @NonNull String base64,
               @NonNull String comment, @NonNull byte[] blob) {
            this.problem = problem;
            this.type = type;
            this.base64 = base64;
            this.comment = comment;
            this.blob = blob;
        }

        public boolean ok() {
            return problem == Problem.NONE;
        }

        /** "type base64": what is sent to GitHub (the comment only names the key). */
        @NonNull
        public String keyLine() {
            return type + " " + base64;
        }

        @NonNull
        public String fingerprint() {
            return DeployKeys.fingerprint(blob);
        }
    }

    @NonNull
    private static Parsed problem(@NonNull Problem problem) {
        return new Parsed(problem, "", "", "", new byte[0]);
    }

    @NonNull
    public static Parsed parse(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return problem(Problem.EMPTY);
        if (text.contains("PRIVATE KEY")) return problem(Problem.PRIVATE_KEY);
        // the first line that looks like a key; a .pub file has exactly one
        String line = null;
        for (String candidate : text.split("\\r?\\n")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                line = trimmed;
                break;
            }
        }
        if (line == null) return problem(Problem.EMPTY);
        String[] parts = line.split("\\s+", 3);
        if (parts.length < 2) return problem(Problem.NOT_A_KEY);
        String type = parts[0];
        if (!TYPES.contains(type)) {
            return problem(type.startsWith("ssh-") || type.startsWith("ecdsa-") || type.startsWith("sk-")
                    ? Problem.UNKNOWN_TYPE : Problem.NOT_A_KEY);
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return problem(Problem.BROKEN);
        }
        // the key data starts with its own type name; a mismatch means a damaged copy
        String inner = innerType(blob);
        if (inner == null || !inner.equals(type)) return problem(Problem.BROKEN);
        String comment = parts.length > 2 ? parts[2].trim() : "";
        return new Parsed(Problem.NONE, type, parts[1], comment, blob);
    }

    /** The first string of an SSH key blob (uint32 length + bytes), or null. */
    @Nullable
    static String innerType(@NonNull byte[] blob) {
        if (blob.length < 4) return null;
        long length = ((blob[0] & 0xFFL) << 24) | ((blob[1] & 0xFFL) << 16) | ((blob[2] & 0xFFL) << 8) | (blob[3] & 0xFFL);
        if (length <= 0 || length > 64 || 4 + length > blob.length) return null;
        return new String(blob, 4, (int) length, StandardCharsets.US_ASCII);
    }

    /** "SHA256:" + Base64 of the SHA-256 of the key data, without "=" (ssh-keygen -l). */
    @NonNull
    public static String fingerprint(@NonNull byte[] blob) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(blob);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            return "";
        }
    }

    /** Fingerprint of a key line as GitHub returns it ("type base64"), "" when unreadable. */
    @NonNull
    public static String fingerprintOf(@Nullable String keyLine) {
        Parsed parsed = parse(keyLine);
        return parsed.ok() ? parsed.fingerprint() : "";
    }

    /** A title for a key the user did not name: its comment, else its type and date. */
    @NonNull
    public static String defaultTitle(@NonNull Parsed parsed, @NonNull String today) {
        if (!parsed.comment.isEmpty()) return parsed.comment;
        return parsed.type + " " + today;
    }
}
