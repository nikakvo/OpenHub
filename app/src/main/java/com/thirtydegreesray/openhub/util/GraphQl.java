package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Blocking GitHub GraphQL calls; GraphQL errors become an ApiException with GitHub's message. */
public final class GraphQl {

    private GraphQl() {
    }

    /** @return the "data" object of the answer */
    @NonNull
    public static JsonObject run(@NonNull String query, @NonNull Map<String, Object> variables) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        JsonObject answer = GitHubApi.call(GitHubApi.service(PullService.class).graphql(body));
        if (answer == null) throw new GitHubApi.ApiException(500, "Empty answer");
        if (answer.has("errors") && answer.get("errors").isJsonArray()
                && answer.getAsJsonArray("errors").size() > 0) {
            throw new GitHubApi.ApiException(422,
                    Json.str(answer.getAsJsonArray("errors").get(0).getAsJsonObject(), "message"));
        }
        JsonObject data = Json.obj(answer, "data");
        return data == null ? new JsonObject() : data;
    }

    /** Variables as pairs: vars("owner", owner, "repo", repo). */
    @NonNull
    public static Map<String, Object> vars(@NonNull Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
