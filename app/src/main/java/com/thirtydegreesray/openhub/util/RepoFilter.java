package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.model.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The filter field of the repository lists: every word of the query must be found in the
 * name, owner/name, description, language or a topic (no case). Repositories whose name
 * matches come first, otherwise the list keeps its order (the sort of the list).
 */
public final class RepoFilter {

    /** Name starts with the first word. */
    static final int NAME_START = 3;
    /** Name contains every word. */
    static final int NAME = 2;
    /** Found somewhere else (owner, description, language, topic). */
    static final int OTHER = 1;
    static final int NONE = 0;

    private RepoFilter() {
    }

    /** The words of a query, lower case; empty for a blank query. */
    @NonNull
    public static List<String> words(@Nullable String query) {
        List<String> words = new ArrayList<>();
        if (query == null) return words;
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) words.add(word);
        }
        return words;
    }

    /** How well one repository matches, {@link #NONE} when a word is missing. */
    static int score(@Nullable String name, @Nullable String fullName, @Nullable String description,
                     @Nullable String language, @Nullable List<String> topics,
                     @NonNull List<String> words) {
        if (words.isEmpty()) return OTHER;
        String lowName = lower(name);
        StringBuilder all = new StringBuilder(lowName).append('\n').append(lower(fullName))
                .append('\n').append(lower(description)).append('\n').append(lower(language));
        if (topics != null) {
            for (String topic : topics) all.append('\n').append(lower(topic));
        }
        String text = all.toString();
        boolean inName = true;
        for (String word : words) {
            if (!text.contains(word)) return NONE;
            if (!lowName.contains(word)) inName = false;
        }
        if (!inName) return OTHER;
        return lowName.startsWith(words.get(0)) ? NAME_START : NAME;
    }

    /** The matching repositories of {@code repos}, best name matches first. */
    @NonNull
    public static ArrayList<Repository> filter(@Nullable List<Repository> repos, @Nullable String query) {
        ArrayList<Repository> result = new ArrayList<>();
        if (repos == null) return result;
        List<String> words = words(query);
        if (words.isEmpty()) {
            result.addAll(repos);
            return result;
        }
        List<List<Repository>> byScore = new ArrayList<>(Collections.nCopies(NAME_START + 1, null));
        for (int i = 0; i <= NAME_START; i++) byScore.set(i, new ArrayList<>());
        for (Repository repo : repos) {
            if (repo == null) continue;
            int score = score(repo.getName(), repo.getFullName(), repo.getDescription(),
                    repo.getLanguage(), repo.getTopics(), words);
            if (score != NONE) byScore.get(score).add(repo);
        }
        for (int i = NAME_START; i > NONE; i--) result.addAll(byScore.get(i));
        return result;
    }

    @NonNull
    private static String lower(@Nullable String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
