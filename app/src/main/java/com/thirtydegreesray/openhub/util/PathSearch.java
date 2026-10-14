package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

/** File name search of the code search screen, used when GitHub's code search finds nothing. */
public final class PathSearch {

    private PathSearch() {
    }

    /** All words in the path; path:, extension: and filename: narrow it like on GitHub. */
    public static boolean matches(@NonNull String path, @NonNull String query) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        for (String token : query.toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("extension:")) {
                if (!name.endsWith("." + token.substring("extension:".length()))) return false;
            } else if (token.startsWith("path:")) {
                if (!lower.contains(token.substring("path:".length()))) return false;
            } else if (token.startsWith("filename:")) {
                if (!name.contains(token.substring("filename:".length()))) return false;
            } else if (token.contains(":")) {
                // other qualifiers (language:, size:...) cannot be applied to a path
            } else if (!lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

}
