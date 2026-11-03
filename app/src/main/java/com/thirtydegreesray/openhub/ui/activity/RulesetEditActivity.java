package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RulesetService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.RulesetForm;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Creates, shows and edits one repository ruleset: name, Active / Disabled, the branches or
 * tags it targets, whether repository admins may bypass it, and the rules that make sense
 * on a phone. Other rules and bypass actors (set on github.com) are shown by name and kept
 * as they are. Read-only for rulesets of the organization and for users who are not admins.
 * The menu has the version history (load an older version into the form, Save restores it)
 * and Delete.
 */
public class RulesetEditActivity extends BaseActivity<IBaseContract.Presenter> {

    /** Opens an existing ruleset. */
    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      long id, @NonNull String target, boolean canEdit) {
        return new Intent(context, RulesetEditActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo)
                        .put("rulesetId", id).put("target", target).put("canEdit", canEdit).build());
    }

    /**
     * A new ruleset for {@code target}; {@code prefill} is the JSON of a ruleset to start
     * from (e.g. converted from classic branch protection), or null.
     */
    @NonNull
    public static Intent createNewIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                         @NonNull String target, @Nullable String prefill) {
        BundleHelper bundle = BundleHelper.builder().put("owner", owner).put("repo", repo)
                .put("rulesetId", 0L).put("target", target).put("canEdit", true);
        if (prefill != null) bundle.put("prefill", prefill);
        return new Intent(context, RulesetEditActivity.class).putExtras(bundle.build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess long rulesetId;
    @AutoAccess String target;
    @AutoAccess boolean canEdit;
    @AutoAccess String prefill;
    /** The form as JSON, so a rotation keeps what was typed. */
    @AutoAccess String formState;
    @AutoAccess String source;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private RulesetForm form;
    private boolean loaded;

    private NestedScrollView scroll;
    private ProgressBar loader;
    private TextView readonlyNote;
    private EditText nameEdit;
    private RadioGroup enforcementGroup;
    private RadioButton enforcementActive;
    private RadioButton enforcementDisabled;
    private RadioButton enforcementEvaluate;
    private TextView targetsTitle;
    private EditText includeEdit;
    private EditText excludeEdit;
    private Button addDefaultBn;
    private Button addAllBn;
    private View targetButtons;
    private TextView targetsNote;
    private CheckBox bypassCheck;
    private RadioGroup bypassModeGroup;
    private RadioButton bypassAlways;
    private RadioButton bypassPullRequest;
    private TextView bypassNote;
    private CheckBox creationCheck;
    private CheckBox updateCheck;
    private CheckBox deletionCheck;
    private CheckBox linearCheck;
    private CheckBox signaturesCheck;
    private CheckBox pullRequestCheck;
    private View pullRequestOptions;
    private EditText approvalsEdit;
    private CheckBox dismissStaleCheck;
    private CheckBox codeOwnerCheck;
    private CheckBox lastPushCheck;
    private CheckBox threadResolutionCheck;
    private CheckBox methodMergeCheck;
    private CheckBox methodSquashCheck;
    private CheckBox methodRebaseCheck;
    private CheckBox statusChecksCheck;
    private View statusChecksOptions;
    private EditText checksEdit;
    private CheckBox strictCheck;
    private CheckBox forcePushCheck;
    private TextView otherRulesNote;
    private View saveBar;
    private View saveDivider;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_ruleset_edit;
    }

    private boolean isNew() {
        return rulesetId <= 0;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        if (StringUtils.isBlank(target)) target = RulesetForm.TARGET_BRANCH;
        setToolbarTitle(getString(isNew() ? R.string.rulesets_new : (canEdit
                        ? R.string.ruleset_edit_title : R.string.ruleset_view_title)),
                owner + "/" + repo);
        if (!StringUtils.isBlank(formState)) {
            // after a rotation: the form as it was
            show(RulesetForm.parse(JsonParser.parseString(formState).getAsJsonObject()));
        } else if (isNew()) {
            show(StringUtils.isBlank(prefill) ? RulesetForm.create(target)
                    : RulesetForm.parse(JsonParser.parseString(prefill).getAsJsonObject()));
        } else {
            load();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (loaded && form != null) {
            readForm();
            formState = form.toJson().toString();
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private RulesetService service() {
        return GitHubApi.service(RulesetService.class);
    }

    private void load() {
        loader.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.INVISIBLE);
        background(() -> GitHubApi.call(service().getRuleset(owner, repo, rulesetId)), ruleset -> {
            loader.setVisibility(View.GONE);
            String type = Json.str(ruleset, "source_type");
            source = Json.str(ruleset, "source");
            if (!type.isEmpty() && !"Repository".equals(type)) canEdit = false;
            show(RulesetForm.parse(ruleset));
        }, error -> {
            loader.setVisibility(View.GONE);
            finish();
        });
    }

    // ---------------------------------------------------------------------------------
    // Form
    // ---------------------------------------------------------------------------------

    private void show(@NonNull RulesetForm f) {
        form = f;
        loaded = true;
        target = f.target;
        scroll.setVisibility(View.VISIBLE);
        boolean branch = f.isBranch();
        boolean push = "push".equals(f.target);

        nameEdit.setText(f.name);
        enforcementEvaluate.setVisibility(RulesetForm.ENFORCEMENT_EVALUATE.equals(f.enforcement)
                ? View.VISIBLE : View.GONE);
        if (RulesetForm.ENFORCEMENT_DISABLED.equals(f.enforcement)) enforcementDisabled.setChecked(true);
        else if (RulesetForm.ENFORCEMENT_EVALUATE.equals(f.enforcement)) enforcementEvaluate.setChecked(true);
        else enforcementActive.setChecked(true);

        targetsTitle.setText(branch ? R.string.ruleset_targets : R.string.ruleset_targets_tags);
        includeEdit.setText(String.join("\n", f.include));
        excludeEdit.setText(String.join("\n", f.exclude));
        addDefaultBn.setVisibility(branch ? View.VISIBLE : View.GONE);
        addAllBn.setText(branch ? R.string.ruleset_add_all : R.string.ruleset_add_all_tags);
        targetsNote.setText(branch ? R.string.ruleset_targets_note : R.string.ruleset_targets_note_tags);

        bypassCheck.setChecked(f.adminsBypass);
        if (RulesetForm.BYPASS_PULL_REQUEST.equals(f.adminsBypassMode)) bypassPullRequest.setChecked(true);
        else bypassAlways.setChecked(true);
        bypassPullRequest.setVisibility(branch ? View.VISIBLE : View.GONE);
        int others = f.otherBypassCount();
        bypassNote.setVisibility(others > 0 ? View.VISIBLE : View.GONE);
        bypassNote.setText(getResources().getQuantityString(R.plurals.ruleset_bypass_others, others, others));

        creationCheck.setChecked(f.creation);
        updateCheck.setChecked(f.update);
        deletionCheck.setChecked(f.deletion);
        linearCheck.setChecked(f.linearHistory);
        signaturesCheck.setChecked(f.signatures);
        forcePushCheck.setChecked(f.nonFastForward);
        linearCheck.setVisibility(branch ? View.VISIBLE : View.GONE);

        pullRequestCheck.setChecked(f.pullRequest);
        pullRequestCheck.setVisibility(branch ? View.VISIBLE : View.GONE);
        approvalsEdit.setText(String.valueOf(f.approvals));
        dismissStaleCheck.setChecked(f.dismissStale);
        codeOwnerCheck.setChecked(f.codeOwnerReview);
        lastPushCheck.setChecked(f.lastPushApproval);
        threadResolutionCheck.setChecked(f.threadResolution);
        methodMergeCheck.setChecked(f.mergeMethods.contains("merge"));
        methodSquashCheck.setChecked(f.mergeMethods.contains("squash"));
        methodRebaseCheck.setChecked(f.mergeMethods.contains("rebase"));

        statusChecksCheck.setChecked(f.statusChecks);
        statusChecksCheck.setVisibility(branch ? View.VISIBLE : View.GONE);
        checksEdit.setText(String.join("\n", f.checkNames()));
        strictCheck.setChecked(f.strictChecks);

        List<String> notes = new ArrayList<>();
        List<String> other = f.otherRuleTypes();
        if (!other.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String type : other) names.add(ruleName(type));
            notes.add(getString(R.string.ruleset_other_rules, String.join(", ", names)));
        }
        for (String code : f.notConverted) {
            notes.add(getString("conversation".equals(code)
                    ? R.string.ruleset_not_converted_conversation : R.string.ruleset_not_converted_restrictions));
        }
        if (push) notes.add(getString(R.string.ruleset_push_note));
        otherRulesNote.setText(String.join("\n\n", notes));
        otherRulesNote.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);

        boolean editable = canEdit && !push;
        if (!editable) {
            readonlyNote.setVisibility(View.VISIBLE);
            readonlyNote.setText(!StringUtils.isBlank(source) && !(owner + "/" + repo).equalsIgnoreCase(source)
                    ? getString(R.string.ruleset_readonly_source, source)
                    : getString(push ? R.string.ruleset_readonly_push : R.string.ruleset_readonly_admin));
        }
        setEditable(findViewById(R.id.form), editable);
        targetButtons.setVisibility(editable ? View.VISIBLE : View.GONE);
        if (!branch) addDefaultBn.setVisibility(View.GONE);
        saveBar.setVisibility(editable ? View.VISIBLE : View.GONE);
        saveDivider.setVisibility(editable ? View.VISIBLE : View.GONE);
        updateOptions();
        invalidateOptionsMenu();
    }

    /** Shows the options of the rules that are ticked. */
    private void updateOptions() {
        boolean branch = form == null || form.isBranch();
        pullRequestOptions.setVisibility(branch && pullRequestCheck.isChecked() ? View.VISIBLE : View.GONE);
        statusChecksOptions.setVisibility(branch && statusChecksCheck.isChecked() ? View.VISIBLE : View.GONE);
        bypassModeGroup.setVisibility(bypassCheck.isChecked() && branch ? View.VISIBLE : View.GONE);
    }

    private static void setEditable(@NonNull View view, boolean editable) {
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            edit.setFocusable(editable);
            edit.setFocusableInTouchMode(editable);
            edit.setCursorVisible(editable);
            edit.setLongClickable(true);
        } else if (view instanceof CompoundButton) {
            view.setEnabled(editable);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEditable(group.getChildAt(i), editable);
        }
    }

    /** Copies what the user set into {@link #form}. */
    private void readForm() {
        form.name = text(nameEdit);
        if (enforcementDisabled.isChecked()) form.enforcement = RulesetForm.ENFORCEMENT_DISABLED;
        else if (enforcementEvaluate.isChecked()) form.enforcement = RulesetForm.ENFORCEMENT_EVALUATE;
        else form.enforcement = RulesetForm.ENFORCEMENT_ACTIVE;
        form.include.clear();
        form.include.addAll(RulesetForm.lines(text(includeEdit)));
        form.exclude.clear();
        form.exclude.addAll(RulesetForm.lines(text(excludeEdit)));
        form.adminsBypass = bypassCheck.isChecked();
        form.adminsBypassMode = bypassPullRequest.isChecked() && form.isBranch()
                ? RulesetForm.BYPASS_PULL_REQUEST : RulesetForm.BYPASS_ALWAYS;
        form.creation = creationCheck.isChecked();
        form.update = updateCheck.isChecked();
        form.deletion = deletionCheck.isChecked();
        form.linearHistory = linearCheck.isChecked();
        form.signatures = signaturesCheck.isChecked();
        form.nonFastForward = forcePushCheck.isChecked();
        form.pullRequest = pullRequestCheck.isChecked();
        int approvals = 0;
        try {
            approvals = Integer.parseInt(text(approvalsEdit));
        } catch (NumberFormatException ignored) {
        }
        form.approvals = Math.max(0, Math.min(RulesetForm.MAX_APPROVALS, approvals));
        form.dismissStale = dismissStaleCheck.isChecked();
        form.codeOwnerReview = codeOwnerCheck.isChecked();
        form.lastPushApproval = lastPushCheck.isChecked();
        form.threadResolution = threadResolutionCheck.isChecked();
        form.mergeMethods.clear();
        if (methodMergeCheck.isChecked()) form.mergeMethods.add("merge");
        if (methodSquashCheck.isChecked()) form.mergeMethods.add("squash");
        if (methodRebaseCheck.isChecked()) form.mergeMethods.add("rebase");
        form.statusChecks = statusChecksCheck.isChecked();
        form.setCheckNames(RulesetForm.lines(text(checksEdit)));
        form.strictChecks = strictCheck.isChecked();
    }

    @NonNull
    private static String text(@NonNull EditText edit) {
        Editable e = edit.getText();
        return e == null ? "" : e.toString().trim();
    }

    private void addTarget(@NonNull String pattern) {
        List<String> lines = RulesetForm.lines(text(includeEdit));
        if (lines.contains(pattern)) return;
        lines.add(pattern);
        includeEdit.setText(String.join("\n", lines));
    }

    // ---------------------------------------------------------------------------------
    // Save, delete, history
    // ---------------------------------------------------------------------------------

    private void save() {
        if (form == null) return;
        readForm();
        String problem = form.problem();
        if (problem != null) {
            switch (problem) {
                case "name":
                    nameEdit.setError(getString(R.string.ruleset_problem_name));
                    nameEdit.requestFocus();
                    break;
                case "include":
                    includeEdit.setError(getString(R.string.ruleset_problem_include));
                    includeEdit.requestFocus();
                    break;
                case "checks":
                    checksEdit.setError(getString(R.string.ruleset_problem_checks));
                    checksEdit.requestFocus();
                    break;
                default:
                    showErrorToast(getString(R.string.ruleset_problem_merge));
            }
            return;
        }
        JsonObject body = form.toJson();
        String name = form.name;
        boolean created = isNew();
        showProgressDialog(getString(R.string.please_wait));
        background(() -> created
                ? GitHubApi.call(service().createRuleset(owner, repo, body))
                : GitHubApi.call(service().updateRuleset(owner, repo, rulesetId, body)), result -> {
            dismissProgressDialog();
            showSuccessToast(getString(created ? R.string.ruleset_created : R.string.ruleset_saved, name));
            setResult(RESULT_OK);
            finish();
        }, error -> dismissProgressDialog());
    }

    private void delete() {
        String name = form == null ? "" : form.name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.rulesets_delete_title)
                .setMessage(getString(R.string.rulesets_delete_message, name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> {
                        GitHubApi.call(service().deleteRuleset(owner, repo, rulesetId));
                        return Boolean.TRUE;
                    }, done -> {
                        dismissProgressDialog();
                        showSuccessToast(getString(R.string.rulesets_deleted, name));
                        setResult(RESULT_OK);
                        finish();
                    }, error -> dismissProgressDialog());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The versions of the ruleset; one can be loaded into the form and saved again. */
    private void showHistory() {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(service().getHistory(owner, repo, rulesetId)), versions -> {
            dismissProgressDialog();
            List<JsonObject> items = new ArrayList<>();
            for (JsonElement e : versions) if (e.isJsonObject()) items.add(e.getAsJsonObject());
            if (items.isEmpty()) {
                showInfoToast(getString(R.string.ruleset_history_none));
                return;
            }
            String me = AppData.INSTANCE.getLoggedUser() == null ? null : AppData.INSTANCE.getLoggedUser().getId();
            String[] labels = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                JsonObject v = items.get(i);
                Date date = Json.date(v, "updated_at");
                String when = date == null ? Json.str(v, "updated_at")
                        : StringUtils.getDateStr(date) + " " + android.text.format.DateFormat
                        .getTimeFormat(this).format(date);
                boolean mine = "User".equals(Json.str(v, "actor.type"))
                        && String.valueOf(Json.integer(v, "actor.id")).equals(me);
                String who = mine ? getString(R.string.ruleset_history_you)
                        : Json.str(v, "actor.type") + " " + Json.integer(v, "actor.id");
                labels[i] = (i == 0 ? getString(R.string.ruleset_history_current) + " · " : "") + when + " · " + who;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.ruleset_history)
                    .setItems(labels, (dialog, which) -> {
                        if (which == 0 || !canEdit) return;
                        loadVersion(Json.integer(items.get(which), "version_id"), labels[which]);
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        }, error -> dismissProgressDialog());
    }

    private void loadVersion(long versionId, @NonNull String label) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(service().getVersion(owner, repo, rulesetId, versionId)), version -> {
            dismissProgressDialog();
            JsonObject state = Json.obj(version, "state");
            if (state == null) return;
            show(RulesetForm.parse(state));
            scroll.scrollTo(0, 0);
            showInfoToast(getString(R.string.ruleset_history_loaded, label));
        }, error -> dismissProgressDialog());
    }

    @NonNull
    private String ruleName(@NonNull String type) {
        switch (type) {
            case "merge_queue": return getString(R.string.ruleset_type_merge_queue);
            case "required_deployments": return getString(R.string.ruleset_type_deployments);
            case "code_scanning": return getString(R.string.ruleset_type_code_scanning);
            case "workflows": return getString(R.string.ruleset_type_workflows);
            case "commit_message_pattern": return getString(R.string.ruleset_type_commit_message);
            case "commit_author_email_pattern": return getString(R.string.ruleset_type_author_email);
            case "committer_email_pattern": return getString(R.string.ruleset_type_committer_email);
            case "branch_name_pattern": return getString(R.string.ruleset_type_branch_name);
            case "tag_name_pattern": return getString(R.string.ruleset_type_tag_name);
            case "file_path_restriction": return getString(R.string.ruleset_type_file_path);
            case "max_file_path_length": return getString(R.string.ruleset_type_path_length);
            case "file_extension_restriction": return getString(R.string.ruleset_type_file_extension);
            case "max_file_size": return getString(R.string.ruleset_type_file_size);
            case "copilot_code_review": return getString(R.string.ruleset_type_copilot);
            default: return type.replace('_', ' ');
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isNew()) {
            menu.add(0, 1, 0, R.string.ruleset_history);
            if (canEdit) menu.add(0, 2, 1, R.string.delete);
            menu.add(0, 3, 2, R.string.open_in_browser);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showHistory();
            return true;
        } else if (item.getItemId() == 2) {
            delete();
            return true;
        } else if (item.getItemId() == 3) {
            AppOpener.openInCustomTabsOrBrowser(this,
                    "https://github.com/" + owner + "/" + repo + "/rules/" + rulesetId);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        scroll = root.findViewById(R.id.scroll);
        loader = root.findViewById(R.id.loader);
        readonlyNote = root.findViewById(R.id.readonly_note);
        nameEdit = root.findViewById(R.id.name_edit);
        enforcementGroup = root.findViewById(R.id.enforcement_group);
        enforcementActive = root.findViewById(R.id.enforcement_active);
        enforcementDisabled = root.findViewById(R.id.enforcement_disabled);
        enforcementEvaluate = root.findViewById(R.id.enforcement_evaluate);
        targetsTitle = root.findViewById(R.id.targets_title);
        includeEdit = root.findViewById(R.id.include_edit);
        excludeEdit = root.findViewById(R.id.exclude_edit);
        targetButtons = root.findViewById(R.id.target_buttons);
        addDefaultBn = root.findViewById(R.id.add_default_bn);
        addAllBn = root.findViewById(R.id.add_all_bn);
        targetsNote = root.findViewById(R.id.targets_note);
        bypassCheck = root.findViewById(R.id.bypass_check);
        bypassModeGroup = root.findViewById(R.id.bypass_mode_group);
        bypassAlways = root.findViewById(R.id.bypass_always);
        bypassPullRequest = root.findViewById(R.id.bypass_pull_request);
        bypassNote = root.findViewById(R.id.bypass_note);
        creationCheck = root.findViewById(R.id.creation_check);
        updateCheck = root.findViewById(R.id.update_check);
        deletionCheck = root.findViewById(R.id.deletion_check);
        linearCheck = root.findViewById(R.id.linear_check);
        signaturesCheck = root.findViewById(R.id.signatures_check);
        pullRequestCheck = root.findViewById(R.id.pull_request_check);
        pullRequestOptions = root.findViewById(R.id.pull_request_options);
        approvalsEdit = root.findViewById(R.id.approvals_edit);
        dismissStaleCheck = root.findViewById(R.id.dismiss_stale_check);
        codeOwnerCheck = root.findViewById(R.id.code_owner_check);
        lastPushCheck = root.findViewById(R.id.last_push_check);
        threadResolutionCheck = root.findViewById(R.id.thread_resolution_check);
        methodMergeCheck = root.findViewById(R.id.method_merge_check);
        methodSquashCheck = root.findViewById(R.id.method_squash_check);
        methodRebaseCheck = root.findViewById(R.id.method_rebase_check);
        statusChecksCheck = root.findViewById(R.id.status_checks_check);
        statusChecksOptions = root.findViewById(R.id.status_checks_options);
        checksEdit = root.findViewById(R.id.checks_edit);
        strictCheck = root.findViewById(R.id.strict_check);
        forcePushCheck = root.findViewById(R.id.force_push_check);
        otherRulesNote = root.findViewById(R.id.other_rules_note);
        saveBar = root.findViewById(R.id.save_bar);
        saveDivider = root.findViewById(R.id.save_divider);

        CompoundButton.OnCheckedChangeListener refresh = (b, checked) -> updateOptions();
        pullRequestCheck.setOnCheckedChangeListener(refresh);
        statusChecksCheck.setOnCheckedChangeListener(refresh);
        bypassCheck.setOnCheckedChangeListener(refresh);
        addDefaultBn.setOnClickListener(v -> addTarget(RulesetForm.DEFAULT_BRANCH));
        addAllBn.setOnClickListener(v -> addTarget(RulesetForm.ALL));
        root.findViewById(R.id.save_bn).setOnClickListener(v -> save());
    }
}
