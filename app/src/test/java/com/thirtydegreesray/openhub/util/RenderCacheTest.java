package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RenderCacheTest {

    private final Set<String> shown = new HashSet<>();
    private final List<String> dropped = new ArrayList<>();
    private final RenderCache<String> cache = new RenderCache<>(3, new RenderCache.Rule<String>() {
        @Override
        public boolean inUse(String value) {
            return shown.contains(value);
        }

        @Override
        public void dropped(String value) {
            dropped.add(value);
        }
    });

    @Test
    public void dropsTheLeastRecentlyUsed() {
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        assertNotNull(cache.get("a")); // a is now the newest
        cache.put("d", "D");
        assertNull(cache.get("b"));
        assertEquals("[B]", dropped.toString());
        assertEquals(3, cache.size());
        assertNotNull(cache.get("a"));
        assertNotNull(cache.get("c"));
        assertNotNull(cache.get("d"));
    }

    @Test
    public void keepsWhatIsShown() {
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        shown.add("A");
        shown.add("B");
        cache.put("d", "D");
        assertEquals("[C]", dropped.toString());
        assertNotNull(cache.get("a"));
        assertNotNull(cache.get("b"));
        // everything shown: the cache grows over its size for a while, and the item just
        // put (about to be shown) is never the one dropped
        shown.add("D");
        cache.put("e", "E");
        assertEquals("E", cache.get("e"));
        assertEquals(4, cache.size());
        assertEquals(1, dropped.size());
        shown.add("E");
        cache.put("f", "F");
        assertTrue(cache.size() > 3);
        shown.clear();
        cache.put("g", "G");
        assertEquals(3, cache.size());
        assertEquals("G", cache.get("g"));
    }

    @Test
    public void replacedAndCleared() {
        cache.put("a", "A");
        cache.put("a", "A2");
        assertEquals("[A]", dropped.toString());
        assertEquals("A2", cache.get("a"));
        cache.put("a", "A2"); // the same item again is not dropped
        assertEquals(1, dropped.size());
        cache.put("b", "B");
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(3, dropped.size());
    }
}
