package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A repository ruleset as the editor shows it, read from and written back to GitHub's JSON
 * (REST "repository rulesets"). Only the rules that make sense on a phone are editable;
 * everything else in a ruleset (other rule types, other bypass actors, extra parameters,
 * organization conditions) is kept exactly as it was, so saving never loses settings made
 * on github.com.
 *
 * Pure Java (Gson only), unit tested.
 */
public final class RulesetForm {

    /** GitHub's id of the "Repository admin" role, for bypass actors. */
    public static final int ADMIN_ROLE_ID = 5;

    public static final String DEFAULT_BRANCH = "~DEFAULT_BRANCH";
    public static final String ALL = "~ALL";

    public static final String TARGET_BRANCH = "branch";
    public static final String TARGET_TAG = "tag";

    public static final String ENFORCEMENT_ACTIVE = "active";
    public static final String ENFORCEMENT_DISABLED = "disabled";
    public static final String ENFORCEMENT_EVALUATE = "evaluate";

    public static final String BYPASS_ALWAYS = "always";
    public static final String BYPASS_PULL_REQUEST = "pull_request";

    /** Rule types the editor knows; any other type is kept untouched. */
    public static final String CREATION = "creation";
    public static final String UPDATE = "update";
    public static final String DELETION = "deletion";
    public static final String LINEAR_HISTORY = "required_linear_history";
    public static final String SIGNATURES = "required_signatures";
    public static final String NON_FAST_FORWARD = "non_fast_forward";
    public static final String PULL_REQUEST = "pull_request";
    public static final String STATUS_CHECKS = "required_status_checks";

    private static final String[] KNOWN = {CREATION, UPDATE, DELETION, LINEAR_HISTORY,
            SIGNATURES, NON_FAST_FORWARD, PULL_REQUEST, STATUS_CHECKS};

    public static final int MAX_APPROVALS = 10;

    /** One required status check: its name, and the app that must report it (or null). */
    public static final class Check {
        @NonNull public final String context;
        @Nullable public final Integer integrationId;

        public Check(@NonNull String context, @Nullable Integer integrationId) {
            this.context = context;
            this.integrationId = integrationId;
        }
    }

    public String name = "";
    public String target = TARGET_BRANCH;
    public String enforcement = ENFORCEMENT_ACTIVE;
    /** Ref patterns as shown to the user: "main", "release/*", ~DEFAULT_BRANCH, ~ALL. */
    public final List<String> include = new ArrayList<>();
    public final List<String> exclude = new ArrayList<>();

    public boolean adminsBypass;
    public String adminsBypassMode = BYPASS_ALWAYS;

    public boolean creation;
    public boolean update;
    public boolean deletion;
    public boolean linearHistory;
    public boolean signatures;
    public boolean nonFastForward;

    public boolean pullRequest;
    public int approvals;
    public boolean dismissStale;
    public boolean codeOwnerReview;
    public boolean lastPushApproval;
    public boolean threadResolution;
    /** Allowed merge methods (merge, squash, rebase); empty is never sent. */
    public final Set<String> mergeMethods = new LinkedHashSet<>();

    public boolean statusChecks;
    public boolean strictChecks;
    public final List<Check> checks = new ArrayList<>();

    /** Kept as they were. */
    private JsonArray otherBypass = new JsonArray();
    private JsonArray otherRules = new JsonArray();
    @Nullable private JsonObject updateParams;
    @Nullable private JsonObject pullRequestParams;
    @Nullable private JsonObject statusCheckParams;
    @Nullable private JsonObject conditions;

    /** Classic protection settings that a ruleset cannot express (after {@link #fromProtection}). */
    public final List<String> notConverted = new ArrayList<>();

    public RulesetForm() {
        mergeMethods.add("merge");
        mergeMethods.add("squash");
        mergeMethods.add("rebase");
    }

    /** A new, empty ruleset for {@code target} (branch or tag). */
    @NonNull
    public static RulesetForm create(@NonNull String target) {
        RulesetForm form = new RulesetForm();
        form.target = target;
        if (TARGET_BRANCH.equals(target)) {
            form.include.add(DEFAULT_BRANCH);
            form.deletion = true;
            form.nonFastForward = true;
        }
        return form;
    }

    // ---------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------

    @NonNull
    public static RulesetForm parse(@NonNull JsonObject ruleset) {
        RulesetForm form = new RulesetForm();
        form.name = str(ruleset, "name");
        String target = str(ruleset, "target");
        form.target = target.isEmpty() ? TARGET_BRANCH : target;
        String enforcement = str(ruleset, "enforcement");
        form.enforcement = enforcement.isEmpty() ? ENFORCEMENT_ACTIVE : enforcement;

        JsonObject conditions = obj(ruleset, "conditions");
        if (conditions != null) {
            form.conditions = conditions.deepCopy();
            JsonObject refName = obj(conditions, "ref_name");
            if (refName != null) {
                for (String ref : strings(refName, "include")) form.include.add(fromRef(ref));
                for (String ref : strings(refName, "exclude")) form.exclude.add(fromRef(ref));
            }
        }

        JsonElement bypass = ruleset.get("bypass_actors");
        if (bypass != null && bypass.isJsonArray()) {
            for (JsonElement e : bypass.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject actor = e.getAsJsonObject();
                if (isAdminRole(actor) && !form.adminsBypass) {
                    form.adminsBypass = true;
                    String mode = str(actor, "bypass_mode");
                    form.adminsBypassMode = mode.isEmpty() ? BYPASS_ALWAYS : mode;
                } else {
                    form.otherBypass.add(actor.deepCopy());
                }
            }
        }

        form.notConverted.addAll(strings(ruleset, PREFILL_NOTES));

        form.mergeMethods.clear();
        boolean mergeMethodsSeen = false;
        JsonElement rules = ruleset.get("rules");
        if (rules != null && rules.isJsonArray()) {
            for (JsonElement e : rules.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject rule = e.getAsJsonObject();
                String type = str(rule, "type");
                JsonObject params = obj(rule, "parameters");
                switch (type) {
                    case CREATION:
                        form.creation = true;
                        break;
                    case UPDATE:
                        form.update = true;
                        form.updateParams = params == null ? null : params.deepCopy();
                        break;
                    case DELETION:
                        form.deletion = true;
                        break;
                    case LINEAR_HISTORY:
                        form.linearHistory = true;
                        break;
                    case SIGNATURES:
                        form.signatures = true;
                        break;
                    case NON_FAST_FORWARD:
                        form.nonFastForward = true;
                        break;
                    case PULL_REQUEST:
                        form.pullRequest = true;
                        if (params != null) {
                            form.pullRequestParams = params.deepCopy();
                            form.approvals = integer(params, "required_approving_review_count");
                            form.dismissStale = bool(params, "dismiss_stale_reviews_on_push");
                            form.codeOwnerReview = bool(params, "require_code_owner_review");
                            form.lastPushApproval = bool(params, "require_last_push_approval");
                            form.threadResolution = bool(params, "required_review_thread_resolution");
                            if (params.has("allowed_merge_methods")) {
                                mergeMethodsSeen = true;
                                form.mergeMethods.addAll(strings(params, "allowed_merge_methods"));
                            }
                        }
                        break;
                    case STATUS_CHECKS:
                        form.statusChecks = true;
                        if (params != null) {
                            form.statusCheckParams = params.deepCopy();
                            form.strictChecks = bool(params, "strict_required_status_checks_policy");
                            JsonElement list = params.get("required_status_checks");
                            if (list != null && list.isJsonArray()) {
                                for (JsonElement c : list.getAsJsonArray()) {
                                    if (!c.isJsonObject()) continue;
                                    String context = str(c.getAsJsonObject(), "context");
                                    if (context.isEmpty()) continue;
                                    JsonElement app = c.getAsJsonObject().get("integration_id");
                                    form.checks.add(new Check(context,
                                            app != null && app.isJsonPrimitive() ? app.getAsInt() : null));
                                }
                            }
                        }
                        break;
                    default:
                        form.otherRules.add(rule.deepCopy());
                }
            }
        }
        if (!mergeMethodsSeen) {
            // not set means every method is allowed
            form.mergeMethods.add("merge");
            form.mergeMethods.add("squash");
            form.mergeMethods.add("rebase");
        }
        return form;
    }

    /** Rule types of this ruleset that the editor does not show (they are kept). */
    @NonNull
    public List<String> otherRuleTypes() {
        List<String> types = new ArrayList<>();
        for (JsonElement e : otherRules) {
            String type = str(e.getAsJsonObject(), "type");
            if (!type.isEmpty() && !types.contains(type)) types.add(type);
        }
        return types;
    }

    /** How many bypass actors other than the repository admins are kept. */
    public int otherBypassCount() {
        return otherBypass.size();
    }

    // ---------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------

    /** Key of {@link #toPrefill} for the {@link #notConverted} codes (never sent to GitHub). */
    private static final String PREFILL_NOTES = "_not_converted";

    /** The form as JSON for a new editor ({@link #toJson} plus what was not converted). */
    @NonNull
    public String toPrefill() {
        JsonObject json = toJson();
        JsonArray notes = new JsonArray();
        for (String code : notConverted) notes.add(code);
        json.add(PREFILL_NOTES, notes);
        return json.toString();
    }

    /** Why the form cannot be saved yet, or null. Codes: "name", "include", "checks", "merge". */
    @Nullable
    public String problem() {
        if (name.trim().isEmpty()) return "name";
        if (include.isEmpty()) return "include";
        if (isBranch() && statusChecks && checks.isEmpty()) return "checks";
        if (isBranch() && pullRequest && mergeMethods.isEmpty()) return "merge";
        return null;
    }

    public boolean isBranch() {
        return TARGET_BRANCH.equals(target);
    }

    /** The body for POST (create) or PUT (update) of a repository ruleset. */
    @NonNull
    public JsonObject toJson() {
        JsonObject body = new JsonObject();
        body.addProperty("name", name.trim());
        body.addProperty("target", target);
        body.addProperty("enforcement", enforcement);

        JsonObject cond = conditions == null ? new JsonObject() : conditions.deepCopy();
        JsonObject refName = new JsonObject();
        refName.add("include", refs(include));
        refName.add("exclude", refs(exclude));
        cond.add("ref_name", refName);
        body.add("conditions", cond);

        JsonArray bypass = new JsonArray();
        if (adminsBypass) {
            JsonObject admin = new JsonObject();
            admin.addProperty("actor_id", ADMIN_ROLE_ID);
            admin.addProperty("actor_type", "RepositoryRole");
            // "only through pull requests" exists for branch rulesets only
            admin.addProperty("bypass_mode", isBranch() ? adminsBypassMode : BYPASS_ALWAYS);
            bypass.add(admin);
        }
        for (JsonElement actor : otherBypass) bypass.add(actor.deepCopy());
        body.add("bypass_actors", bypass);

        JsonArray rules = new JsonArray();
        if (creation) rules.add(rule(CREATION, null));
        if (update) rules.add(rule(UPDATE, updateParamsOrDefault()));
        if (deletion) rules.add(rule(DELETION, null));
        if (signatures) rules.add(rule(SIGNATURES, null));
        if (nonFastForward) rules.add(rule(NON_FAST_FORWARD, null));
        if (isBranch()) {
            if (linearHistory) rules.add(rule(LINEAR_HISTORY, null));
            if (pullRequest) rules.add(rule(PULL_REQUEST, pullRequestParamsJson()));
            if (statusChecks && !checks.isEmpty()) rules.add(rule(STATUS_CHECKS, statusCheckParamsJson()));
        }
        for (JsonElement rule : otherRules) rules.add(rule.deepCopy());
        body.add("rules", rules);
        return body;
    }

    @NonNull
    private JsonObject updateParamsOrDefault() {
        if (updateParams != null) return updateParams.deepCopy();
        JsonObject params = new JsonObject();
        params.addProperty("update_allows_fetch_and_merge", false);
        return params;
    }

    @NonNull
    private JsonObject pullRequestParamsJson() {
        JsonObject params = pullRequestParams == null ? new JsonObject() : pullRequestParams.deepCopy();
        params.addProperty("required_approving_review_count",
                Math.max(0, Math.min(MAX_APPROVALS, approvals)));
        params.addProperty("dismiss_stale_reviews_on_push", dismissStale);
        params.addProperty("require_code_owner_review", codeOwnerReview);
        params.addProperty("require_last_push_approval", lastPushApproval);
        params.addProperty("required_review_thread_resolution", threadResolution);
        JsonArray methods = new JsonArray();
        for (String method : new String[]{"merge", "squash", "rebase"}) {
            if (mergeMethods.contains(method)) methods.add(method);
        }
        params.add("allowed_merge_methods", methods);
        return params;
    }

    @NonNull
    private JsonObject statusCheckParamsJson() {
        JsonObject params = statusCheckParams == null ? new JsonObject() : statusCheckParams.deepCopy();
        params.addProperty("strict_required_status_checks_policy", strictChecks);
        JsonArray list = new JsonArray();
        for (Check check : checks) {
            JsonObject c = new JsonObject();
            c.addProperty("context", check.context);
            if (check.integrationId != null) c.addProperty("integration_id", check.integrationId);
            list.add(c);
        }
        params.add("required_status_checks", list);
        return params;
    }

    /** Sets the checks from the user's lines, keeping the app of a check that stayed. */
    public void setCheckNames(@NonNull List<String> names) {
        List<Check> old = new ArrayList<>(checks);
        checks.clear();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : names) {
            String name = raw.trim();
            if (name.isEmpty() || !seen.add(name)) continue;
            Integer app = null;
            for (Check c : old) {
                if (c.context.equals(name)) {
                    app = c.integrationId;
                    break;
                }
            }
            checks.add(new Check(name, app));
        }
    }

    @NonNull
    public List<String> checkNames() {
        List<String> names = new ArrayList<>();
        for (Check c : checks) names.add(c.context);
        return names;
    }

    @NonNull
    private JsonArray refs(@NonNull List<String> patterns) {
        JsonArray array = new JsonArray();
        Set<String> seen = new LinkedHashSet<>();
        for (String pattern : patterns) {
            String ref = toRef(pattern, target);
            if (!ref.isEmpty() && seen.add(ref)) array.add(ref);
        }
        return array;
    }

    @NonNull
    private static JsonObject rule(@NonNull String type, @Nullable JsonObject params) {
        JsonObject rule = new JsonObject();
        rule.addProperty("type", type);
        if (params != null) rule.add("parameters", params);
        return rule;
    }

    // ---------------------------------------------------------------------------------
    // Ref patterns
    // ---------------------------------------------------------------------------------

    /**
     * "main" -> "refs/heads/main" (tags: "refs/tags/..."); ~DEFAULT_BRANCH, ~ALL and full
     * refs stay as they are.
     */
    @NonNull
    public static String toRef(@NonNull String pattern, @NonNull String target) {
        String p = pattern.trim();
        if (p.isEmpty()) return "";
        if (p.startsWith("~") || p.startsWith("refs/")) return p;
        return (TARGET_TAG.equals(target) ? "refs/tags/" : "refs/heads/") + p;
    }

    /** "refs/heads/main" -> "main", the special names stay. */
    @NonNull
    public static String fromRef(@NonNull String ref) {
        if (ref.startsWith("refs/heads/")) return ref.substring("refs/heads/".length());
        if (ref.startsWith("refs/tags/")) return ref.substring("refs/tags/".length());
        return ref;
    }

    /** Lines of a text field, trimmed, without empty ones. */
    @NonNull
    public static List<String> lines(@Nullable String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    // ---------------------------------------------------------------------------------
    // Classic branch protection -> ruleset
    // ---------------------------------------------------------------------------------

    /**
     * A new branch ruleset with the same effect as the classic protection of {@code branch}
     * (GET .../branches/{branch}/protection). What a ruleset cannot express is listed in
     * {@link #notConverted} (codes: "conversation", "restrictions").
     */
    @NonNull
    public static RulesetForm fromProtection(@NonNull String branch, @NonNull JsonObject protection) {
        RulesetForm form = new RulesetForm();
        form.target = TARGET_BRANCH;
        form.name = branch;
        form.include.add(branch);

        JsonObject reviews = obj(protection, "required_pull_request_reviews");
        boolean conversation = enabled(protection, "required_conversation_resolution");
        if (reviews != null) {
            form.pullRequest = true;
            form.approvals = integer(reviews, "required_approving_review_count");
            form.dismissStale = bool(reviews, "dismiss_stale_reviews");
            form.codeOwnerReview = bool(reviews, "require_code_owner_reviews");
            form.lastPushApproval = bool(reviews, "require_last_push_approval");
            form.threadResolution = conversation;
        } else if (conversation) {
            // in a ruleset this is part of "Require a pull request"
            form.notConverted.add("conversation");
        }

        JsonObject checks = obj(protection, "required_status_checks");
        if (checks != null) {
            form.strictChecks = bool(checks, "strict");
            JsonElement list = checks.get("checks");
            if (list != null && list.isJsonArray() && list.getAsJsonArray().size() > 0) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    String context = str(e.getAsJsonObject(), "context");
                    if (context.isEmpty()) continue;
                    JsonElement app = e.getAsJsonObject().get("app_id");
                    form.checks.add(new Check(context,
                            app != null && app.isJsonPrimitive() ? app.getAsInt() : null));
                }
            } else {
                for (String context : strings(checks, "contexts")) form.checks.add(new Check(context, null));
            }
            form.statusChecks = !form.checks.isEmpty();
        }

        // classic protection does not bind admins unless "apply to administrators" is on
        form.adminsBypass = !enabled(protection, "enforce_admins");
        form.adminsBypassMode = BYPASS_ALWAYS;
        form.linearHistory = enabled(protection, "required_linear_history");
        form.signatures = enabled(protection, "required_signatures");
        form.nonFastForward = !enabled(protection, "allow_force_pushes");
        form.deletion = !enabled(protection, "allow_deletions");
        form.update = enabled(protection, "lock_branch");
        if (form.update && enabled(protection, "allow_fork_syncing")) {
            JsonObject params = new JsonObject();
            params.addProperty("update_allows_fetch_and_merge", true);
            form.updateParams = params;
        }
        form.creation = enabled(protection, "block_creations");
        JsonObject restrictions = obj(protection, "restrictions");
        if (restrictions != null) form.notConverted.add("restrictions");
        return form;
    }

    // ---------------------------------------------------------------------------------

    private static boolean isAdminRole(@NonNull JsonObject actor) {
        JsonElement id = actor.get("actor_id");
        return "RepositoryRole".equals(str(actor, "actor_type"))
                && id != null && id.isJsonPrimitive() && id.getAsInt() == ADMIN_ROLE_ID;
    }

    private static boolean enabled(@NonNull JsonObject object, @NonNull String key) {
        JsonObject value = obj(object, key);
        return value != null && bool(value, "enabled");
    }

    @NonNull
    private static String str(@NonNull JsonObject object, @NonNull String key) {
        JsonElement e = object.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static boolean bool(@NonNull JsonObject object, @NonNull String key) {
        JsonElement e = object.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static int integer(@NonNull JsonObject object, @NonNull String key) {
        JsonElement e = object.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0;
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isNumber() ? p.getAsInt() : 0;
    }

    @Nullable
    private static JsonObject obj(@NonNull JsonObject object, @NonNull String key) {
        JsonElement e = object.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    @NonNull
    private static List<String> strings(@NonNull JsonObject object, @NonNull String key) {
        List<String> result = new ArrayList<>();
        JsonElement e = object.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                if (item.isJsonPrimitive()) result.add(item.getAsString());
            }
        }
        return result;
    }

    /** True for rule types the editor shows. */
    public static boolean isKnown(@NonNull String type) {
        for (String k : KNOWN) if (k.equals(type)) return true;
        return false;
    }
}
