package com.thirtydegreesray.openhub.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MergeRulesTest {

    private static JsonArray reviews(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    private static JsonObject rule(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    public void latestDecisiveReviewCounts() {
        JsonArray list = reviews("["
                + "{\"user\":{\"login\":\"a\"},\"state\":\"APPROVED\"},"
                + "{\"user\":{\"login\":\"a\"},\"state\":\"COMMENTED\"},"
                + "{\"user\":{\"login\":\"b\"},\"state\":\"APPROVED\"},"
                + "{\"user\":{\"login\":\"b\"},\"state\":\"CHANGES_REQUESTED\"},"
                + "{\"user\":{\"login\":\"c\"},\"state\":\"APPROVED\"},"
                + "{\"user\":{\"login\":\"c\"},\"state\":\"DISMISSED\"},"
                + "{\"user\":{\"login\":\"d\"},\"state\":\"PENDING\"}]");
        assertEquals(1, MergeRules.approvals(list));
        assertEquals(1, MergeRules.changesRequested(list));
    }

    @Test
    public void laterApprovalReplacesChangesRequested() {
        JsonArray list = reviews("["
                + "{\"user\":{\"login\":\"a\"},\"state\":\"CHANGES_REQUESTED\"},"
                + "{\"user\":{\"login\":\"a\"},\"state\":\"APPROVED\"}]");
        assertEquals(1, MergeRules.approvals(list));
        assertEquals(0, MergeRules.changesRequested(list));
        assertEquals(0, MergeRules.approvals(null));
    }

    @Test
    public void pullRequestParameters() {
        JsonObject r = rule("{\"type\":\"pull_request\",\"parameters\":{"
                + "\"required_approving_review_count\":2,\"required_review_thread_resolution\":true}}");
        assertEquals(2, MergeRules.requiredApprovals(r));
        assertTrue(MergeRules.requiresResolution(r));
        JsonObject none = rule("{\"type\":\"pull_request\"}");
        assertEquals(0, MergeRules.requiredApprovals(none));
        assertFalse(MergeRules.requiresResolution(none));
    }

    @Test
    public void onlyMergeRulesCount() {
        assertTrue(MergeRules.aboutMerging("pull_request"));
        assertTrue(MergeRules.aboutMerging("required_status_checks"));
        assertFalse(MergeRules.aboutMerging("copilot_code_review"));
        assertFalse(MergeRules.aboutMerging("deletion"));
        assertFalse(MergeRules.aboutMerging("non_fast_forward"));
        assertFalse(MergeRules.aboutMerging("creation"));
        assertFalse(MergeRules.aboutMerging(""));
    }

    @Test
    public void readableNames() {
        assertEquals("Copilot code review", MergeRules.readable("copilot_code_review"));
        assertEquals("X", MergeRules.readable("x"));
        assertEquals("", MergeRules.readable(""));
    }
}
