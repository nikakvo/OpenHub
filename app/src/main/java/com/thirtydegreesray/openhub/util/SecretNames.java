package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * GitHub's rules for Actions secret names: letters, digits and underscores, not starting
 * with a digit or with GITHUB_. GitHub stores them in upper case.
 */
public final class SecretNames {

    public static final int OK = 0;
    public static final int EMPTY = 1;
    public static final int BAD_CHARACTERS = 2;
    public static final int STARTS_WITH_DIGIT = 3;
    public static final int GITHUB_PREFIX = 4;

    /** GitHub refuses larger values. */
    public static final int MAX_VALUE_BYTES = 48 * 1024;

    private SecretNames() {
    }

    /** The name as GitHub will store it: trimmed and in upper case. */
    @NonNull
    public static String normalize(@Nullable String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    public static int check(@Nullable String name) {
        String value = normalize(name);
        if (value.isEmpty()) return EMPTY;
        if (!value.matches("[A-Z0-9_]+")) return BAD_CHARACTERS;
        if (Character.isDigit(value.charAt(0))) return STARTS_WITH_DIGIT;
        if (value.startsWith("GITHUB_")) return GITHUB_PREFIX;
        return OK;
    }
}
