package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stacked pull requests: a chain of pull requests in one repository where each one
 * targets the branch of the one below it. Position 1 is the bottom (closest to the
 * stack's base branch). Pure Java, so the decisions can be unit tested.
 */
public final class PullStacks {

    private PullStacks() {
    }

    /** One pull request of a stack, as GET repos/o/r/stacks/{n} lists it. */
    public static final class Entry {
        public final int number;
        @NonNull public final String title;
        @NonNull public final String baseRef;
        @NonNull public final String headRef;
        public final boolean open;
        public final boolean merged;
        public final boolean draft;

        public Entry(int number, @Nullable String title, @Nullable String baseRef, @Nullable String headRef,
                     boolean open, boolean merged, boolean draft) {
            this.number = number;
            this.title = title == null ? "" : title;
            this.baseRef = baseRef == null ? "" : baseRef;
            this.headRef = headRef == null ? "" : headRef;
            this.open = open;
            this.merged = merged;
            this.draft = draft;
        }

        /** Still to be merged: open and not merged. */
        public boolean waiting() {
            return open && !merged;
        }
    }

    /**
     * The stack from the bottom (position 1) to the top. GitHub lists the pull requests in
     * stack order; that order is kept when it agrees with the position GitHub reports for
     * the current pull request (also when it comes top first). Otherwise the chain is
     * rebuilt from the branches: a pull request sits on the one whose head branch is its
     * base. After a partial merge the open rest is retargeted to the stack base, so there
     * can be several chains; finished chains (merged or closed) come first.
     */
    @NonNull
    public static List<Entry> order(@NonNull List<Entry> items, @NonNull String stackBase,
                                    int current, int position) {
        if (items.isEmpty()) return new ArrayList<>();
        if (position >= 1 && position <= items.size()) {
            if (items.get(position - 1).number == current) return new ArrayList<>(items);
            List<Entry> reversed = new ArrayList<>(items);
            Collections.reverse(reversed);
            if (reversed.get(position - 1).number == current) return reversed;
        }
        return chains(items, stackBase);
    }

    @NonNull
    static List<Entry> chains(@NonNull List<Entry> items, @NonNull String stackBase) {
        Set<String> heads = new HashSet<>();
        Map<String, List<Entry>> byBase = new HashMap<>();
        for (Entry e : items) {
            heads.add(e.headRef);
            List<Entry> list = byBase.get(e.baseRef);
            if (list == null) byBase.put(e.baseRef, list = new ArrayList<>());
            list.add(e);
        }
        // bottoms: their base is no head inside the stack (usually the stack base)
        List<Entry> bottoms = new ArrayList<>();
        for (Entry e : items) {
            if (!heads.contains(e.baseRef) || e.baseRef.equals(stackBase)) bottoms.add(e);
        }
        List<List<Entry>> chains = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Entry bottom : bottoms) {
            if (used.contains(bottom.number)) continue;
            List<Entry> chain = new ArrayList<>();
            Entry at = bottom;
            while (at != null && used.add(at.number)) {
                chain.add(at);
                Entry next = null;
                List<Entry> above = byBase.get(at.headRef);
                if (above != null) {
                    for (Entry candidate : above) {
                        if (!used.contains(candidate.number)) {
                            next = candidate;
                            break;
                        }
                    }
                }
                at = next;
            }
            chains.add(chain);
        }
        // finished chains first: they were the bottom of the stack before their merge
        List<Entry> result = new ArrayList<>();
        for (List<Entry> chain : chains) if (finished(chain)) result.addAll(chain);
        for (List<Entry> chain : chains) if (!finished(chain)) result.addAll(chain);
        // anything that did not fit a chain keeps GitHub's order at the end
        for (Entry e : items) if (!used.contains(e.number)) result.add(e);
        return result;
    }

    private static boolean finished(@NonNull List<Entry> chain) {
        for (Entry e : chain) if (e.waiting()) return false;
        return true;
    }

    /**
     * The pull requests one merge of {@code current} lands, bottom first: every open one
     * below it plus itself (GitHub always merges from the bottom up). Empty when
     * {@code current} is not in the stack.
     */
    @NonNull
    public static List<Entry> mergedWith(@NonNull List<Entry> ordered, int current) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : ordered) {
            if (e.waiting() || e.number == current) result.add(e);
            if (e.number == current) return result;
        }
        return new ArrayList<>();
    }

    /** "#11, #12 and #13" for the pull requests other than {@code current}. */
    @NonNull
    public static String numbers(@NonNull List<Entry> entries, int current, @NonNull String and) {
        List<String> parts = new ArrayList<>();
        for (Entry e : entries) if (e.number != current) parts.add("#" + e.number);
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " " + and + " " + parts.get(parts.size() - 1);
    }
}
