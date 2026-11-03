package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure decisions behind "why is this pull request blocked": which rules of the base branch
 * are about merging, how many approvals a pull request has and needs, and a readable name
 * for a rule type the app has no own text for.
 */
public final class MergeRules {

    /** Rule types that never stop a merge (creating, deleting, force pushing, Copilot asking to review). */
    private static final Set<String> NOT_ABOUT_MERGING = new HashSet<>(Arrays.asList(
            RulesetForm.CREATION, RulesetForm.DELETION, RulesetForm.NON_FAST_FORWARD,
            "branch_name_pattern", "tag_name_pattern",
            "copilot_code_review"));

    private MergeRules() {
    }

    /** True when the rule can stop a pull request from being merged into the branch. */
    public static boolean aboutMerging(@NonNull String type) {
        return !type.isEmpty() && !NOT_ABOUT_MERGING.contains(type);
    }

    /**
     * Reviewers whose latest decisive review is an approval. GitHub lists reviews oldest
     * first; a later "changes requested" or a dismissal replaces an earlier approval, a plain
     * comment changes nothing.
     */
    public static int approvals(@Nullable JsonArray reviews) {
        int count = 0;
        for (String state : latestStates(reviews).values()) {
            if ("APPROVED".equals(state)) count++;
        }
        return count;
    }

    /** Reviewers whose latest decisive review asks for changes. */
    public static int changesRequested(@Nullable JsonArray reviews) {
        int count = 0;
        for (String state : latestStates(reviews).values()) {
            if ("CHANGES_REQUESTED".equals(state)) count++;
        }
        return count;
    }

    @NonNull
    private static Map<String, String> latestStates(@Nullable JsonArray reviews) {
        Map<String, String> states = new LinkedHashMap<>();
        if (reviews == null) return states;
        for (JsonElement e : reviews) {
            if (!e.isJsonObject()) continue;
            JsonObject review = e.getAsJsonObject();
            String user = Json.str(review, "user.login");
            String state = Json.str(review, "state");
            if (user.isEmpty()) continue;
            if (state.equals("APPROVED") || state.equals("CHANGES_REQUESTED") || state.equals("DISMISSED")) {
                states.put(user, state);
            }
        }
        return states;
    }

    /** Approvals the pull request rule asks for, 0 when the rule has none. */
    public static int requiredApprovals(@NonNull JsonObject rule) {
        return Math.max(0, Json.integer(rule, "parameters.required_approving_review_count"));
    }

    /** True when the pull request rule asks for every conversation to be resolved. */
    public static boolean requiresResolution(@NonNull JsonObject rule) {
        return Json.bool(rule, "parameters.required_review_thread_resolution");
    }

    /** "copilot_code_review" -> "Copilot code review". */
    @NonNull
    public static String readable(@NonNull String type) {
        String text = type.replace('_', ' ').trim();
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
