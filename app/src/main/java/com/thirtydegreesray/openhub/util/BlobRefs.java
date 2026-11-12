package com.thirtydegreesray.openhub.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reading a blob link again when GitHub answered 404: in github.com/o/r/blob/a/b/c.md the
 * branch may be "a" or "a/b" (branch names may contain "/"). Pure Java, no Android.
 */
public final class BlobRefs {

    private BlobRefs() {
    }

    /** "feature/x" + "docs/a.md" -> [feature, x, docs, a.md] */
    public static List<String> segments(String ref, String path) {
        List<String> out = new ArrayList<>();
        addParts(out, ref);
        addParts(out, path);
        return out;
    }

    private static void addParts(List<String> out, String text) {
        if (text == null) return;
        for (String part : text.split("/")) {
            if (!part.isEmpty()) out.add(part);
        }
    }

    /** segments[from, to) joined with "/" */
    public static String join(List<String> segments, int from, int to) {
        return String.join("/", segments.subList(from, to));
    }

    /**
     * How many leading segments form the longest ref that exists (branch or tag names
     * without "refs/heads/"), 0 when none does. At least one segment always stays for the
     * file path.
     */
    public static int longestRef(List<String> segments, Collection<String> refs) {
        if (segments == null || refs == null || refs.isEmpty()) return 0;
        for (int k = segments.size() - 1; k >= 1; k--) {
            if (refs.contains(join(segments, 0, k))) return k;
        }
        return 0;
    }

    /** A ref name from a matching-refs answer: "refs/heads/feature/x" -> "feature/x". */
    public static String shortName(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("refs/heads/")) return ref.substring("refs/heads/".length());
        if (ref.startsWith("refs/tags/")) return ref.substring("refs/tags/".length());
        return ref;
    }

    /** A commit id as used in permalinks (7 to 40 hex digits). */
    public static boolean looksLikeSha(String ref) {
        if (ref == null || ref.length() < 7 || ref.length() > 40) return false;
        return ref.toLowerCase(Locale.ROOT).matches("[0-9a-f]+");
    }

    /** Names from refs/heads/... and refs/tags/... lists, without the prefixes. */
    public static List<String> shortNames(Collection<String> refs) {
        if (refs == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String r : refs) {
            String s = shortName(r);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }
}
