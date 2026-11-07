package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Archived pull requests. GitHub has no "archived" field on a pull request; the only
 * source is the search qualifier {@code is:archived}, which answers only for people with
 * triage access or higher. Search can lag a few seconds behind an archive or unarchive.
 * Runs on a background thread.
 */
public final class ArchivedPulls {

    private ArchivedPulls() {
    }

    @NonNull
    public static String query(@NonNull String owner, @NonNull String repo) {
        return "repo:" + owner + "/" + repo + " is:pr is:archived";
    }

    /** Numbers of the archived pull requests (newest 150; archiving is for a few spam pull requests). */
    @NonNull
    public static Set<Integer> numbers(@NonNull String owner, @NonNull String repo) throws IOException {
        Set<Integer> numbers = new HashSet<>();
        PullService pulls = GitHubApi.service(PullService.class);
        for (int page = 1; page <= 3; page++) {
            JsonObject answer = GitHubApi.call(pulls.searchIssues(query(owner, repo), page));
            JsonElement items = Json.get(answer, "items");
            if (items == null || !items.isJsonArray()) break;
            JsonArray array = items.getAsJsonArray();
            for (JsonElement e : array) numbers.add(Json.integer(e.getAsJsonObject(), "number"));
            if (array.size() < 50) break;
        }
        return numbers;
    }
}
