package com.thirtydegreesray.openhub.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RulesetFormTest {

    /** A ruleset as GET /repos/{owner}/{repo}/rulesets/{id} returns it (shortened). */
    private static final String RULESET = "{"
            + "\"id\":42,\"name\":\"main protection\",\"target\":\"branch\","
            + "\"source_type\":\"Repository\",\"source\":\"nikakvo/test\",\"enforcement\":\"active\","
            + "\"conditions\":{\"ref_name\":{\"include\":[\"~DEFAULT_BRANCH\",\"refs/heads/release/*\"],"
            + "\"exclude\":[\"refs/heads/release/old\"]}},"
            + "\"bypass_actors\":[{\"actor_id\":5,\"actor_type\":\"RepositoryRole\",\"bypass_mode\":\"pull_request\"},"
            + "{\"actor_id\":null,\"actor_type\":\"DeployKey\",\"bypass_mode\":\"always\"}],"
            + "\"rules\":[{\"type\":\"deletion\"},{\"type\":\"non_fast_forward\"},"
            + "{\"type\":\"pull_request\",\"parameters\":{\"required_approving_review_count\":2,"
            + "\"dismiss_stale_reviews_on_push\":true,\"require_code_owner_review\":false,"
            + "\"require_last_push_approval\":true,\"required_review_thread_resolution\":false,"
            + "\"allowed_merge_methods\":[\"squash\"],\"required_reviewers\":[{\"minimum_approvals\":1}]}},"
            + "{\"type\":\"required_status_checks\",\"parameters\":{\"strict_required_status_checks_policy\":true,"
            + "\"do_not_enforce_on_create\":true,"
            + "\"required_status_checks\":[{\"context\":\"build\",\"integration_id\":15368},{\"context\":\"lint\"}]}},"
            + "{\"type\":\"code_scanning\",\"parameters\":{\"code_scanning_tools\":[{\"tool\":\"CodeQL\","
            + "\"alerts_threshold\":\"errors\",\"security_alerts_threshold\":\"high_or_higher\"}]}}]}";

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    public void readsEverythingTheEditorShows() {
        RulesetForm form = RulesetForm.parse(json(RULESET));
        assertEquals("main protection", form.name);
        assertEquals("branch", form.target);
        assertEquals("active", form.enforcement);
        assertEquals(Arrays.asList("~DEFAULT_BRANCH", "release/*"), form.include);
        assertEquals(Arrays.asList("release/old"), form.exclude);
        assertTrue(form.adminsBypass);
        assertEquals("pull_request", form.adminsBypassMode);
        assertEquals(1, form.otherBypassCount());
        assertTrue(form.deletion);
        assertTrue(form.nonFastForward);
        assertFalse(form.creation);
        assertTrue(form.pullRequest);
        assertEquals(2, form.approvals);
        assertTrue(form.dismissStale);
        assertTrue(form.lastPushApproval);
        assertEquals(1, form.mergeMethods.size());
        assertTrue(form.mergeMethods.contains("squash"));
        assertTrue(form.statusChecks);
        assertTrue(form.strictChecks);
        assertEquals(Arrays.asList("build", "lint"), form.checkNames());
        assertEquals(Arrays.asList("code_scanning"), form.otherRuleTypes());
    }

    @Test
    public void savingKeepsWhatTheEditorDoesNotShow() {
        RulesetForm form = RulesetForm.parse(json(RULESET));
        JsonObject body = form.toJson();
        // the deploy key bypass, the code scanning rule and extra parameters survive
        assertEquals(2, body.getAsJsonArray("bypass_actors").size());
        String text = body.toString();
        assertTrue(text.contains("\"DeployKey\""));
        assertTrue(text.contains("\"code_scanning\""));
        assertTrue(text.contains("\"required_reviewers\""));
        assertTrue(text.contains("\"do_not_enforce_on_create\":true"));
        // the app of the "build" check stays
        assertTrue(text.contains("{\"context\":\"build\",\"integration_id\":15368}"));
        JsonArray include = body.getAsJsonObject("conditions").getAsJsonObject("ref_name")
                .getAsJsonArray("include");
        assertEquals("~DEFAULT_BRANCH", include.get(0).getAsString());
        assertEquals("refs/heads/release/*", include.get(1).getAsString());
        // no read-only fields are sent back
        assertFalse(body.has("id"));
        assertFalse(body.has("source"));
    }

    @Test
    public void editedValuesAreWritten() {
        RulesetForm form = RulesetForm.parse(json(RULESET));
        form.enforcement = RulesetForm.ENFORCEMENT_DISABLED;
        form.adminsBypass = false;
        form.pullRequest = false;
        form.setCheckNames(Arrays.asList("build", " test ", "build", ""));
        form.creation = true;
        JsonObject body = form.toJson();
        assertEquals("disabled", body.get("enforcement").getAsString());
        assertEquals(1, body.getAsJsonArray("bypass_actors").size());
        String text = body.toString();
        assertFalse(text.contains("\"pull_request\""));
        assertTrue(text.contains("{\"type\":\"creation\"}"));
        assertTrue(text.contains("{\"context\":\"build\",\"integration_id\":15368},{\"context\":\"test\"}"));
    }

    @Test
    public void tagRulesetsGetNoBranchOnlyRules() {
        RulesetForm form = RulesetForm.create(RulesetForm.TARGET_TAG);
        form.name = "tags";
        form.include.add("v*");
        form.pullRequest = true;
        form.linearHistory = true;
        form.deletion = true;
        form.adminsBypass = true;
        form.adminsBypassMode = RulesetForm.BYPASS_PULL_REQUEST;
        JsonObject body = form.toJson();
        String text = body.toString();
        assertFalse(text.contains("pull_request\"}") || text.contains("\"type\":\"pull_request\""));
        assertFalse(text.contains("required_linear_history"));
        assertTrue(text.contains("refs/tags/v*"));
        assertTrue(text.contains("\"bypass_mode\":\"always\""));
    }

    @Test
    public void problems() {
        RulesetForm form = RulesetForm.create(RulesetForm.TARGET_BRANCH);
        assertEquals("name", form.problem());
        form.name = "x";
        assertNull(form.problem());
        form.include.clear();
        assertEquals("include", form.problem());
        form.include.add("main");
        form.statusChecks = true;
        assertEquals("checks", form.problem());
        form.setCheckNames(Arrays.asList("ci"));
        form.pullRequest = true;
        form.mergeMethods.clear();
        assertEquals("merge", form.problem());
    }

    @Test
    public void newBranchRulesetDefaults() {
        RulesetForm form = RulesetForm.create(RulesetForm.TARGET_BRANCH);
        assertEquals(Arrays.asList("~DEFAULT_BRANCH"), form.include);
        assertTrue(form.deletion);
        assertTrue(form.nonFastForward);
        assertEquals(3, form.mergeMethods.size());
    }

    @Test
    public void refs() {
        assertEquals("refs/heads/main", RulesetForm.toRef(" main ", "branch"));
        assertEquals("refs/tags/v1.*", RulesetForm.toRef("v1.*", "tag"));
        assertEquals("~ALL", RulesetForm.toRef("~ALL", "branch"));
        assertEquals("refs/heads/x", RulesetForm.toRef("refs/heads/x", "tag"));
        assertEquals("", RulesetForm.toRef("  ", "branch"));
        assertEquals("main", RulesetForm.fromRef("refs/heads/main"));
        assertEquals("v1", RulesetForm.fromRef("refs/tags/v1"));
        assertEquals("~DEFAULT_BRANCH", RulesetForm.fromRef("~DEFAULT_BRANCH"));
        assertEquals(Arrays.asList("a", "b/c"), RulesetForm.lines(" a \n\n b/c \r\n"));
    }

    @Test
    public void classicProtectionBecomesARuleset() {
        // GET .../branches/main/protection (shortened)
        JsonObject protection = json("{"
                + "\"required_status_checks\":{\"strict\":true,\"contexts\":[\"ci\"],"
                + "\"checks\":[{\"context\":\"ci\",\"app_id\":15368}]},"
                + "\"required_pull_request_reviews\":{\"dismiss_stale_reviews\":true,"
                + "\"require_code_owner_reviews\":false,\"required_approving_review_count\":1,"
                + "\"require_last_push_approval\":false},"
                + "\"required_signatures\":{\"enabled\":false},"
                + "\"enforce_admins\":{\"enabled\":false},"
                + "\"required_linear_history\":{\"enabled\":true},"
                + "\"allow_force_pushes\":{\"enabled\":false},"
                + "\"allow_deletions\":{\"enabled\":false},"
                + "\"block_creations\":{\"enabled\":false},"
                + "\"required_conversation_resolution\":{\"enabled\":true},"
                + "\"lock_branch\":{\"enabled\":false},"
                + "\"allow_fork_syncing\":{\"enabled\":false}}");
        RulesetForm form = RulesetForm.fromProtection("main", protection);
        assertEquals("main", form.name);
        assertEquals(Arrays.asList("main"), form.include);
        assertTrue(form.pullRequest);
        assertEquals(1, form.approvals);
        assertTrue(form.dismissStale);
        assertTrue(form.threadResolution);
        assertTrue(form.statusChecks);
        assertTrue(form.strictChecks);
        assertEquals(Integer.valueOf(15368), form.checks.get(0).integrationId);
        assertTrue(form.adminsBypass);
        assertTrue(form.linearHistory);
        assertTrue(form.nonFastForward);
        assertTrue(form.deletion);
        assertFalse(form.update);
        assertFalse(form.creation);
        assertTrue(form.notConverted.isEmpty());
        assertNull(form.problem());
        assertTrue(form.toJson().toString().contains("\"refs/heads/main\""));
    }

    @Test
    public void whatCannotBeConvertedIsNamed() {
        JsonObject protection = json("{\"enforce_admins\":{\"enabled\":true},"
                + "\"allow_force_pushes\":{\"enabled\":true},\"allow_deletions\":{\"enabled\":true},"
                + "\"required_conversation_resolution\":{\"enabled\":true},"
                + "\"restrictions\":{\"users\":[],\"teams\":[],\"apps\":[]}}");
        RulesetForm form = RulesetForm.fromProtection("dev", protection);
        assertFalse(form.adminsBypass);
        assertFalse(form.nonFastForward);
        assertFalse(form.deletion);
        assertFalse(form.pullRequest);
        assertEquals(Arrays.asList("conversation", "restrictions"), form.notConverted);
    }
}
