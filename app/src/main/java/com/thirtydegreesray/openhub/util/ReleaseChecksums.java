package com.thirtydegreesray.openhub.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The SHA256SUMS file of a release, in the format of {@code sha256sum} (and
 * {@code sha256sum -c}): one line per file, "hex, two spaces, name", sorted by name.
 *
 * The checksums are the ones GitHub computed for every uploaded file (the asset's
 * "digest", "sha256:hex"), so nothing has to be downloaded or hashed on the phone.
 * Files uploaded before GitHub kept digests have none and are left out.
 */
public final class ReleaseChecksums {

    public static final String FILE_NAME = "SHA256SUMS";
    private static final String PREFIX = "sha256:";

    private ReleaseChecksums() {
    }

    public static final class Result {
        /** The file's text; empty when no file has a checksum. */
        public final String text;
        /** How many files it lists. */
        public final int count;
        /** Files left out because GitHub has no checksum for them, sorted. */
        public final List<String> missing;

        Result(String text, int count, List<String> missing) {
            this.text = text;
            this.count = count;
            this.missing = missing;
        }
    }

    /** @param digestsByName asset name to GitHub's digest (may be null or blank) */
    public static Result build(Map<String, String> digestsByName) {
        TreeMap<String, String> sorted = new TreeMap<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : digestsByName.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isEmpty() || FILE_NAME.equals(name)) continue;
            String hex = hex(entry.getValue());
            if (hex == null) {
                missing.add(name);
            } else {
                sorted.put(name, hex);
            }
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            text.append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        Collections.sort(missing);
        return new Result(text.toString(), sorted.size(), missing);
    }

    /** The lowercase hex of a "sha256:..." digest, or null if it is not one. */
    static String hex(String digest) {
        if (digest == null) return null;
        String value = digest.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith(PREFIX)) return null;
        value = value.substring(PREFIX.length()).toLowerCase(Locale.ROOT);
        if (value.length() != 64) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return null;
        }
        return value;
    }
}
