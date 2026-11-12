package com.thirtydegreesray.openhub.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rendered items of a long list, kept so a row bound again shows at once: at most
 * {@code max} of them, the ones used longest ago go first, but never one that is shown on
 * the screen now. Main thread only. Pure Java, tested in RenderCacheTest.
 */
public final class RenderCache<V> {

    public interface Rule<V> {
        /** True while the item is shown (it is kept even when the cache is over its size). */
        boolean inUse(V value);

        /** The item left the cache: free what it holds (pictures). */
        void dropped(V value);
    }

    private final int max;
    private final Rule<V> rule;
    private final LinkedHashMap<String, V> items = new LinkedHashMap<>(16, 0.75f, true);

    public RenderCache(int max, Rule<V> rule) {
        this.max = Math.max(1, max);
        this.rule = rule;
    }

    /** The item, now the most recently used one; null when it is not kept. */
    public V get(String key) {
        return items.get(key);
    }

    public void put(String key, V value) {
        V old = items.put(key, value);
        if (old != null && old != value) rule.dropped(old);
        trim(key);
    }

    public int size() {
        return items.size();
    }

    public void clear() {
        List<V> all = new ArrayList<>(items.values());
        items.clear();
        for (V value : all) rule.dropped(value);
    }

    /** Drops the oldest items over the size; never {@code keep} (put now, shown next). */
    private void trim(String keep) {
        if (items.size() <= max) return;
        List<V> dropped = new ArrayList<>();
        Iterator<Map.Entry<String, V>> it = items.entrySet().iterator();
        while (items.size() > max && it.hasNext()) {
            Map.Entry<String, V> entry = it.next();
            V value = entry.getValue();
            if (entry.getKey().equals(keep) || rule.inUse(value)) continue;
            it.remove();
            dropped.add(value);
        }
        for (V value : dropped) rule.dropped(value);
    }
}
