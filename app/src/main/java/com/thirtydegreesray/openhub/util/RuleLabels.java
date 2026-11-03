package com.thirtydegreesray.openhub.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Names of ruleset rules, the same on every screen (ruleset editor, Rules for this branch,
 * the merge box of a pull request).
 */
public final class RuleLabels {

    private RuleLabels() {
    }

    /** The plain name of a rule type. */
    @NonNull
    public static String name(@NonNull Context ctx, @NonNull String type) {
        switch (type) {
            case RulesetForm.CREATION: return ctx.getString(R.string.ruleset_rule_creation);
            case RulesetForm.UPDATE: return ctx.getString(R.string.ruleset_rule_update);
            case RulesetForm.DELETION: return ctx.getString(R.string.ruleset_rule_deletion);
            case RulesetForm.LINEAR_HISTORY: return ctx.getString(R.string.ruleset_rule_linear);
            case RulesetForm.SIGNATURES: return ctx.getString(R.string.ruleset_rule_signatures);
            case RulesetForm.NON_FAST_FORWARD: return ctx.getString(R.string.ruleset_rule_force_push);
            case RulesetForm.PULL_REQUEST: return ctx.getString(R.string.ruleset_rule_pull_request);
            case RulesetForm.STATUS_CHECKS: return ctx.getString(R.string.ruleset_rule_status_checks);
            case "merge_queue": return ctx.getString(R.string.ruleset_type_merge_queue);
            case "required_deployments": return ctx.getString(R.string.ruleset_type_deployments);
            case "code_scanning": return ctx.getString(R.string.ruleset_type_code_scanning);
            case "workflows": return ctx.getString(R.string.ruleset_type_workflows);
            case "commit_message_pattern": return ctx.getString(R.string.ruleset_type_commit_message);
            case "commit_author_email_pattern": return ctx.getString(R.string.ruleset_type_author_email);
            case "committer_email_pattern": return ctx.getString(R.string.ruleset_type_committer_email);
            case "branch_name_pattern": return ctx.getString(R.string.ruleset_type_branch_name);
            case "tag_name_pattern": return ctx.getString(R.string.ruleset_type_tag_name);
            case "file_path_restriction": return ctx.getString(R.string.ruleset_type_file_path);
            case "max_file_path_length": return ctx.getString(R.string.ruleset_type_path_length);
            case "file_extension_restriction": return ctx.getString(R.string.ruleset_type_file_extension);
            case "max_file_size": return ctx.getString(R.string.ruleset_type_file_size);
            case "copilot_code_review": return ctx.getString(R.string.ruleset_type_copilot);
            default: return MergeRules.readable(type);
        }
    }

    /** The name plus its main setting: approvals of a pull request rule, names of checks. */
    @NonNull
    public static String label(@NonNull Context ctx, @NonNull JsonObject rule) {
        String type = Json.str(rule, "type");
        String name = name(ctx, type);
        if (RulesetForm.PULL_REQUEST.equals(type)) {
            int approvals = MergeRules.requiredApprovals(rule);
            return approvals > 0 ? name + " (" + ctx.getResources().getQuantityString(
                    R.plurals.ruleset_approvals_count, approvals, approvals) + ")" : name;
        }
        if (RulesetForm.STATUS_CHECKS.equals(type)) {
            List<String> checks = new ArrayList<>();
            JsonElement list = Json.get(rule, "parameters.required_status_checks");
            if (list != null && list.isJsonArray()) {
                for (JsonElement c : list.getAsJsonArray()) {
                    if (c.isJsonObject()) checks.add(Json.str(c.getAsJsonObject(), "context"));
                }
            }
            return checks.isEmpty() ? name : name + ": " + String.join(", ", checks);
        }
        return name;
    }
}
