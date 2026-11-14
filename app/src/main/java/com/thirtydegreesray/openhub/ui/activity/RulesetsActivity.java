package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.http.RulesetService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ErrorTexts;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.RulesetForm;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The rulesets of a repository (Settings, Rules, Rulesets on github.com): the repository's
 * own and those of its organization that apply to it. Admins can create, switch on and
 * off, edit and delete the repository's own rulesets; everybody else can look at them.
 */
public class RulesetsActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, RulesetsActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    private static final int MAX_PAGES = 5;

    @AutoAccess String owner;
    @AutoAccess String repo;

    private LinearLayout list;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<JsonObject> rulesets = new ArrayList<>();
    private boolean isAdmin;

    private final ActivityResultLauncher<Intent> editor = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) load();
            });

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_rulesets;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.rulesets), owner + "/" + repo);
        PullRefresh.wrap((View) list.getParent(), this::load).follow(loader);
        load();
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
        background(() -> {
            JsonObject repoJson = GitHubApi.call(GitHubApi.service(RepoSettingsService.class).getRepo(owner, repo));
            List<JsonObject> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonArray array = GitHubApi.call(service().getRulesets(owner, repo, page));
                if (array == null || array.size() == 0) break;
                for (JsonElement e : array) if (e.isJsonObject()) all.add(e.getAsJsonObject());
                if (array.size() < 100) break;
            }
            return new Object[]{repoJson, all};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            isAdmin = Json.bool((JsonObject) result[0], "permissions.admin");
            rulesets.clear();
            //noinspection unchecked
            rulesets.addAll((List<JsonObject>) result[1]);
            render();
        }, error -> loader.setVisibility(View.GONE));
    }

    private void render() {
        list.removeAllViews();
        emptyText.setVisibility(rulesets.isEmpty() ? View.VISIBLE : View.GONE);
        if (isAdmin) newBn.show();
        else newBn.hide();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (JsonObject ruleset : rulesets) {
            View row = inflater.inflate(R.layout.layout_setting_row, list, false);
            ((TextView) row.findViewById(R.id.setting_title)).setText(Json.str(ruleset, "name"));
            List<String> meta = new ArrayList<>();
            meta.add(targetLabel(Json.str(ruleset, "target")));
            meta.add(enforcementLabel(Json.str(ruleset, "enforcement")));
            if (!isOwn(ruleset)) {
                meta.add(getString(R.string.rulesets_from, Json.str(ruleset, "source")));
            }
            ((TextView) row.findViewById(R.id.setting_summary)).setText(String.join(" · ", meta));
            row.setOnClickListener(v -> open(ruleset));
            row.setOnLongClickListener(v -> {
                showMenu(v, ruleset);
                return true;
            });
            list.addView(row);
        }
    }

    /** The repository's own ruleset (not one of the organization or enterprise). */
    private boolean isOwn(@NonNull JsonObject ruleset) {
        String type = Json.str(ruleset, "source_type");
        return type.isEmpty() || "Repository".equals(type);
    }

    @NonNull
    private String targetLabel(@NonNull String target) {
        switch (target) {
            case "tag":
                return getString(R.string.ruleset_target_tag);
            case "push":
                return getString(R.string.ruleset_target_push);
            default:
                return getString(R.string.ruleset_target_branch);
        }
    }

    @NonNull
    private String enforcementLabel(@NonNull String enforcement) {
        switch (enforcement) {
            case RulesetForm.ENFORCEMENT_DISABLED:
                return getString(R.string.ruleset_disabled);
            case RulesetForm.ENFORCEMENT_EVALUATE:
                return getString(R.string.ruleset_evaluate);
            default:
                return getString(R.string.ruleset_active);
        }
    }

    private void open(@NonNull JsonObject ruleset) {
        editor.launch(RulesetEditActivity.createIntent(this, owner, repo,
                Json.integer(ruleset, "id"), Json.str(ruleset, "target"), isAdmin && isOwn(ruleset)));
    }

    private void showMenu(@NonNull View anchor, @NonNull JsonObject ruleset) {
        boolean canEdit = isAdmin && isOwn(ruleset);
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, canEdit ? R.string.edit : R.string.rulesets_view);
        boolean disabled = RulesetForm.ENFORCEMENT_DISABLED.equals(Json.str(ruleset, "enforcement"));
        if (canEdit) {
            popup.getMenu().add(0, 2, 1, disabled ? R.string.rulesets_enable : R.string.rulesets_disable);
            popup.getMenu().add(0, 3, 2, R.string.delete);
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) open(ruleset);
            else if (item.getItemId() == 2) setEnforcement(ruleset, disabled
                    ? RulesetForm.ENFORCEMENT_ACTIVE : RulesetForm.ENFORCEMENT_DISABLED);
            else if (item.getItemId() == 3) delete(ruleset);
            return true;
        });
        popup.show();
    }

    private void setEnforcement(@NonNull JsonObject ruleset, @NonNull String enforcement) {
        long id = Json.integer(ruleset, "id");
        String name = Json.str(ruleset, "name");
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("enforcement", enforcement);
            GitHubApi.call(service().updateRuleset(owner, repo, id, body));
            return Boolean.TRUE;
        }, getString(RulesetForm.ENFORCEMENT_DISABLED.equals(enforcement)
                ? R.string.rulesets_disabled_done : R.string.rulesets_enabled_done, name));
    }

    private void delete(@NonNull JsonObject ruleset) {
        long id = Json.integer(ruleset, "id");
        String name = Json.str(ruleset, "name");
        new AlertDialog.Builder(this)
                .setTitle(R.string.rulesets_delete_title)
                .setMessage(getString(R.string.rulesets_delete_message, name))
                .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                    GitHubApi.call(service().deleteRuleset(owner, repo, id));
                    return Boolean.TRUE;
                }, getString(R.string.rulesets_deleted, name)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createRuleset() {
        String[] targets = {getString(R.string.ruleset_target_branch), getString(R.string.ruleset_target_tag)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.rulesets_new)
                .setItems(targets, (dialog, which) -> editor.launch(RulesetEditActivity.createNewIntent(this,
                        owner, repo, which == 0 ? RulesetForm.TARGET_BRANCH : RulesetForm.TARGET_TAG, null)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.refresh);
        menu.add(0, 2, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            load();
            return true;
        } else if (item.getItemId() == 2) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo + "/rules");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------

    /** Runs a change, then reloads the list. */
    private void run(@NonNull Callable<Boolean> work, @NonNull String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            showSuccessToast(successMessage);
            load();
        }, error -> dismissProgressDialog());
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    showErrorToast(ErrorTexts.of(error));
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        list = root.findViewById(R.id.ruleset_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        newBn = root.findViewById(R.id.new_ruleset_bn);
        newBn.setOnClickListener(v -> createRuleset());
    }
}
