package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Date;

/** Null-safe reading of GitHub JSON answers. */
public final class Json {

    private Json() {
    }

    @Nullable
    public static JsonElement get(@Nullable JsonObject object, @NonNull String path) {
        JsonElement current = object;
        for (String key : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
        }
        return current == null || current.isJsonNull() ? null : current;
    }

    /** {@code path} may be nested: "head.ref". */
    @NonNull
    public static String str(@Nullable JsonObject object, @NonNull String path) {
        JsonElement value = get(object, path);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    public static boolean bool(@Nullable JsonObject object, @NonNull String path) {
        JsonElement value = get(object, path);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    public static int integer(@Nullable JsonObject object, @NonNull String path) {
        JsonElement value = get(object, path);
        return value == null || !value.isJsonPrimitive() ? 0 : value.getAsInt();
    }

    @Nullable
    public static JsonObject obj(@Nullable JsonObject object, @NonNull String path) {
        JsonElement value = get(object, path);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    @Nullable
    public static Date date(@Nullable JsonObject object, @NonNull String path) {
        String text = str(object, path);
        if (text.isEmpty()) return null;
        try {
            return Date.from(Instant.parse(text));
        } catch (Exception e) {
            return null;
        }
    }
}
