package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The search history of the Search screen, kept in one preference as "newest$$older$$...".
 * Pure Java (the preference is read and written by the caller), so it is unit tested.
 */
public final class SearchHistory {

    private SearchHistory() {
    }

    /** At most this many searches are remembered. */
    public static final int MAX = 30;

    private static final String SEPARATOR = "$$";

    @NonNull
    public static ArrayList<String> parse(@Nullable String stored) {
        ArrayList<String> list = new ArrayList<>();
        if (stored == null || stored.trim().isEmpty()) return list;
        for (String part : stored.split("\\$\\$")) {
            if (!part.trim().isEmpty() && !list.contains(part)) list.add(part);
        }
        return list;
    }

    @NonNull
    public static String join(@NonNull List<String> list) {
        return String.join(SEPARATOR, list);
    }

    /**
     * Puts a search on top (moving it if it was there already); the oldest falls out past
     * {@link #MAX}. A search with "$" cannot be stored (it would break the separator).
     */
    @NonNull
    public static ArrayList<String> add(@NonNull List<String> list, @Nullable String search) {
        ArrayList<String> out = new ArrayList<>(list);
        if (search == null || search.trim().isEmpty() || search.contains("$")) return out;
        out.remove(search);
        out.add(0, search);
        while (out.size() > MAX) out.remove(out.size() - 1);
        return out;
    }

    @NonNull
    public static ArrayList<String> remove(@NonNull List<String> list, @Nullable String search) {
        ArrayList<String> out = new ArrayList<>(list);
        out.remove(search);
        return out;
    }
}
