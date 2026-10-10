package com.thirtydegreesray.openhub.util.state;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copies {@link AutoAccess} fields of an object to and from a Bundle.
 *
 * Same API and behavior as the old com.thirtydegreesray:dataautoaccess library, but
 * runtime reflection instead of an annotation processor:
 * - the key is {@link AutoAccess#dataName()} or the field name
 * - annotated fields of superclasses are included
 * - getData only touches fields whose key is present in the bundle
 * - for an Activity, intent extras are read first, then the saved state
 * - saveData skips null values
 */
public final class DataAutoAccess {

    private static final String TAG = "DataAutoAccess";

    private static final Map<Class<?>, List<Entry>> CACHE = new ConcurrentHashMap<>();

    private DataAutoAccess() {
    }

    private static final class Entry {
        final Field field;
        final String key;

        Entry(Field field, String key) {
            this.field = field;
            this.key = key;
        }
    }

    /**
     * Reads every annotated field of {@code target} that has a value in {@code bundle}.
     *
     * For an Activity the intent extras are applied first and {@code bundle} (saved
     * state) on top, like the original library: on first launch there is no saved
     * state and the values come from the intent that started the screen.
     */
    public static void getData(@Nullable Object target, @Nullable Bundle bundle) {
        if (target == null) return;
        if (target instanceof Activity) {
            Intent intent = ((Activity) target).getIntent();
            Bundle extras = intent == null ? null : intent.getExtras();
            if (extras != null && extras != bundle) read(target, extras);
        }
        read(target, bundle);
    }

    private static void read(@NonNull Object target, @Nullable Bundle bundle) {
        if (bundle == null || bundle.isEmpty()) return;
        bundle.setClassLoader(target.getClass().getClassLoader());
        for (Entry entry : entriesOf(target.getClass())) {
            if (!bundle.containsKey(entry.key)) continue;
            try {
                @SuppressWarnings("deprecation")
                Object value = bundle.get(entry.key);
                if (value == null && entry.field.getType().isPrimitive()) continue;
                entry.field.set(target, value);
            } catch (Exception e) {
                Log.w(TAG, "getData " + target.getClass().getSimpleName() + "." + entry.key, e);
            }
        }
    }

    /** Writes every non-null annotated field of {@code target} into {@code bundle}. */
    public static void saveData(@Nullable Object target, @Nullable Bundle bundle) {
        if (target == null || bundle == null) return;
        for (Entry entry : entriesOf(target.getClass())) {
            try {
                Object value = entry.field.get(target);
                if (value != null) put(bundle, entry.key, value);
            } catch (Exception e) {
                Log.w(TAG, "saveData " + target.getClass().getSimpleName() + "." + entry.key, e);
            }
        }
    }

    @NonNull
    private static List<Entry> entriesOf(@NonNull Class<?> type) {
        List<Entry> cached = CACHE.get(type);
        if (cached != null) return cached;
        List<Entry> entries = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("android.") || name.startsWith("androidx.") || name.startsWith("java.")) {
                break;
            }
            for (Field field : c.getDeclaredFields()) {
                AutoAccess annotation = field.getAnnotation(AutoAccess.class);
                if (annotation == null || Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                String key = annotation.dataName().isEmpty() ? field.getName() : annotation.dataName();
                entries.add(new Entry(field, key));
            }
        }
        List<Entry> result = Collections.unmodifiableList(entries);
        CACHE.put(type, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void put(@NonNull Bundle bundle, @NonNull String key, @NonNull Object value) {
        if (value instanceof String) bundle.putString(key, (String) value);
        else if (value instanceof Integer) bundle.putInt(key, (Integer) value);
        else if (value instanceof Boolean) bundle.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) bundle.putLong(key, (Long) value);
        else if (value instanceof Float) bundle.putFloat(key, (Float) value);
        else if (value instanceof Double) bundle.putDouble(key, (Double) value);
        else if (value instanceof Short) bundle.putShort(key, (Short) value);
        else if (value instanceof Byte) bundle.putByte(key, (Byte) value);
        else if (value instanceof Character) bundle.putChar(key, (Character) value);
        else if (value instanceof String[]) bundle.putStringArray(key, (String[]) value);
        else if (value instanceof Bundle) bundle.putBundle(key, (Bundle) value);
        else if (value instanceof Parcelable) bundle.putParcelable(key, (Parcelable) value);
        else if (value instanceof Parcelable[]) bundle.putParcelableArray(key, (Parcelable[]) value);
        else if (value instanceof ArrayList) putList(bundle, key, (ArrayList<Object>) value);
        else if (value instanceof CharSequence) bundle.putCharSequence(key, (CharSequence) value);
        else if (value instanceof Serializable) bundle.putSerializable(key, (Serializable) value);
        else Log.w(TAG, "Unsupported type for '" + key + "': " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static void putList(@NonNull Bundle bundle, @NonNull String key, @NonNull ArrayList<Object> list) {
        boolean allParcelable = true;
        boolean allStrings = true;
        for (Object item : list) {
            if (!(item instanceof Parcelable)) allParcelable = false;
            if (!(item instanceof String)) allStrings = false;
        }
        if (!list.isEmpty() && allParcelable) {
            bundle.putParcelableArrayList(key, (ArrayList<? extends Parcelable>) (ArrayList<?>) list);
        } else if (allStrings) {
            bundle.putStringArrayList(key, (ArrayList<String>) (ArrayList<?>) list);
        } else {
            bundle.putSerializable(key, list);
        }
    }
}
