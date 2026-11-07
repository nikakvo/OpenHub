package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PullStacksTest {

    private static PullStacks.Entry open(int n, String base, String head) {
        return new PullStacks.Entry(n, "PR " + n, base, head, true, false, false);
    }

    private static PullStacks.Entry merged(int n, String base, String head) {
        return new PullStacks.Entry(n, "PR " + n, base, head, false, true, false);
    }

    private static String numbers(List<PullStacks.Entry> list) {
        StringBuilder b = new StringBuilder();
        for (PullStacks.Entry e : list) b.append(b.length() == 0 ? "" : ",").append(e.number);
        return b.toString();
    }

    private final List<PullStacks.Entry> stack = Arrays.asList(
            open(11, "main", "a"), open(12, "a", "b"), open(13, "b", "c"));

    @Test
    public void keepsGitHubOrderWhenPositionAgrees() {
        assertEquals("11,12,13", numbers(PullStacks.order(stack, "main", 12, 2)));
        assertEquals("11,12,13", numbers(PullStacks.order(stack, "main", 13, 3)));
    }

    @Test
    public void turnsATopFirstListAround() {
        List<PullStacks.Entry> topFirst = new ArrayList<>(stack);
        java.util.Collections.reverse(topFirst);
        assertEquals("11,12,13", numbers(PullStacks.order(topFirst, "main", 11, 1)));
    }

    @Test
    public void rebuildsTheChainFromBranches() {
        List<PullStacks.Entry> mixed = Arrays.asList(stack.get(2), stack.get(0), stack.get(1));
        // position 2 = #12, but neither the list nor its reverse has #12 there
        assertEquals("11,12,13", numbers(PullStacks.order(mixed, "main", 12, 2)));
        assertEquals("11,12,13", numbers(PullStacks.order(mixed, "main", 12, 0)));
    }

    @Test
    public void mergedChainComesBeforeTheRetargetedRest() {
        // #11 and #12 merged; #13 was retargeted to main, #14 still sits on #13
        List<PullStacks.Entry> after = Arrays.asList(
                open(13, "main", "c"), open(14, "c", "d"), merged(11, "main", "a"), merged(12, "a", "b"));
        assertEquals("11,12,13,14", numbers(PullStacks.order(after, "main", 13, 0)));
    }

    @Test
    public void mergeTakesEveryOpenPullBelow() {
        assertEquals("11,12", numbers(PullStacks.mergedWith(stack, 12)));
        assertEquals("11,12,13", numbers(PullStacks.mergedWith(stack, 13)));
        assertEquals("11", numbers(PullStacks.mergedWith(stack, 11)));
        List<PullStacks.Entry> partly = Arrays.asList(
                merged(11, "main", "a"), open(12, "main", "b"), open(13, "b", "c"));
        assertEquals("12,13", numbers(PullStacks.mergedWith(partly, 13)));
        assertTrue(PullStacks.mergedWith(stack, 99).isEmpty());
    }

    @Test
    public void numbersText() {
        assertEquals("", PullStacks.numbers(PullStacks.mergedWith(stack, 11), 11, "and"));
        assertEquals("#11", PullStacks.numbers(PullStacks.mergedWith(stack, 12), 12, "and"));
        assertEquals("#11 and #12", PullStacks.numbers(PullStacks.mergedWith(stack, 13), 13, "and"));
        List<PullStacks.Entry> four = new ArrayList<>(stack);
        four.add(open(14, "c", "d"));
        assertEquals("#11, #12 and #13", PullStacks.numbers(PullStacks.mergedWith(four, 14), 14, "and"));
    }
}
