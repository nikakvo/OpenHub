package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchHistoryTest {

    @Test
    public void parseAndJoin() {
        assertTrue(SearchHistory.parse(null).isEmpty());
        assertTrue(SearchHistory.parse("").isEmpty());
        assertEquals(Arrays.asList("zygisk", "hma oss"), SearchHistory.parse("zygisk$$hma oss"));
        // empty parts and repeats left by older versions are dropped
        assertEquals(Arrays.asList("a", "b"), SearchHistory.parse("a$$$$b$$a"));
        assertEquals("zygisk$$hma oss", SearchHistory.join(Arrays.asList("zygisk", "hma oss")));
        assertEquals("", SearchHistory.join(new ArrayList<>()));
    }

    @Test
    public void addMovesToTheTop() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("d", "a", "b", "c"), SearchHistory.add(list, "d"));
        assertEquals(Arrays.asList("c", "a", "b"), SearchHistory.add(list, "c"));
        assertEquals(list, SearchHistory.add(list, "with $ sign"));
        assertEquals(list, SearchHistory.add(list, "  "));
    }

    @Test
    public void addKeepsAtMostMax() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < SearchHistory.MAX; i++) list.add("s" + i);
        List<String> out = SearchHistory.add(list, "new");
        assertEquals(SearchHistory.MAX, out.size());
        assertEquals("new", out.get(0));
        assertEquals("s" + (SearchHistory.MAX - 2), out.get(out.size() - 1));
    }

    @Test
    public void removeOne() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("a", "c"), SearchHistory.remove(list, "b"));
        assertEquals(list, SearchHistory.remove(list, "x"));
    }
}
