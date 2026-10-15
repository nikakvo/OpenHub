package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.SecurityService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.SealedBox;
import com.thirtydegreesray.openhub.util.SecretNames;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The Security tab of a repository, like on github.com: Dependabot, code scanning and
 * secret scanning alerts (dismiss, close, reopen; admins can turn the scanners on), and
 * for admins the repository secrets of GitHub Actions (values are sealed on the phone
 * with the repository's public key, as GitHub requires).
 */
public class SecurityActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, boolean admin) {
        return new Intent(context, SecurityActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo)
                        .put("admin", admin).build());
    }

    private static final int TAB_DEPENDABOT = 0;
    private static final int TAB_CODE_SCANNING = 1;
    private static final int TAB_SECRET_SCANNING = 2;
    private static final int TAB_SECRETS = 3;

    private static final int PER_PAGE = 100;
    private static final int MAX_SECRET_PAGES = 3;

    private static final int COLOR_CRITICAL = Color.parseColor("#E5534B");
    private static final int COLOR_HIGH = Color.parseColor("#DB6D28");
    private static final int COLOR_MEDIUM = Color.parseColor("#C69026");

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean admin;
    @AutoAccess int tab;
    @AutoAccess boolean showClosed;

    private TabLayout tabLayout;
    private SwipeRefreshLayout refreshLayout;
    private boolean refreshing;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private Button turnOnBn;
    private LinearLayout itemList;
    private TextView footerText;
    private FloatingActionButton addSecretBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Disposable loading;
    private final List<String> secretNames = new ArrayList<>();

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_security;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.security), owner + "/" + repo);

        tabLayout.setVisibility(View.VISIBLE);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.security_dependabot));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.security_code_scanning));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.security_secret_scanning));
        if (admin) tabLayout.addTab(tabLayout.newTab().setText(R.string.security_secrets));
        if (tab >= tabLayout.getTabCount()) tab = TAB_DEPENDABOT;
        TabLayout.Tab selected = tabLayout.getTabAt(tab);
        if (selected != null) selected.select();
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                invalidateOptionsMenu();
                load();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab t) {
            }
        });

        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(this));
        refreshLayout.setOnRefreshListener(this::load);
        addSecretBn.setOnClickListener(v -> editSecret(null));
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        if (loading != null) loading.dispose();
        super.onDestroy();
    }

    private SecurityService service() {
        return GitHubApi.service(SecurityService.class);
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.security_show_closed).setCheckable(true);
        menu.add(0, 2, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem closed = menu.findItem(1);
        closed.setVisible(tab != TAB_SECRETS);
        closed.setChecked(showClosed);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                showClosed = !showClosed;
                item.setChecked(showClosed);
                load();
                return true;
            case 2:
                AppOpener.openInCustomTabsOrBrowser(this, pageUrl());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @NonNull
    private String pageUrl() {
        String base = "https://github.com/" + owner + "/" + repo;
        switch (tab) {
            case TAB_DEPENDABOT:
                return base + "/security/dependabot";
            case TAB_CODE_SCANNING:
                return base + "/security/code-scanning";
            case TAB_SECRET_SCANNING:
                return base + "/security/secret-scanning";
            default:
                return base + "/settings/secrets/actions";
        }
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    /** Posted: a SwipeRefreshLayout told before its first layout keeps a stuck circle. */
    private void showRefreshing(boolean on) {
        refreshing = on;
        refreshLayout.post(() -> refreshLayout.setRefreshing(refreshing));
    }

    private void load() {
        if (loading != null) loading.dispose();
        final int shownTab = tab;
        showRefreshing(true);
        if (shownTab == TAB_SECRETS) addSecretBn.show();
        else addSecretBn.hide();
        loading = Single.fromCallable(() -> fetch(shownTab))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    showRefreshing(false);
                    if (shownTab == tab) render(items);
                }, error -> {
                    showRefreshing(false);
                    if (shownTab == tab) showError(error);
                });
    }

    @NonNull
    private List<JsonObject> fetch(int forTab) throws Exception {
        List<JsonObject> items = new ArrayList<>();
        JsonArray array;
        switch (forTab) {
            case TAB_DEPENDABOT:
                array = GitHubApi.call(service().getDependabotAlerts(owner, repo,
                        showClosed ? "dismissed,fixed,auto_dismissed" : "open", PER_PAGE));
                break;
            case TAB_CODE_SCANNING:
                array = GitHubApi.call(service().getCodeScanningAlerts(owner, repo,
                        showClosed ? "closed" : "open", PER_PAGE));
                break;
            case TAB_SECRET_SCANNING:
                array = GitHubApi.call(service().getSecretScanningAlerts(owner, repo,
                        showClosed ? "resolved" : "open", PER_PAGE));
                break;
            default:
                for (int page = 1; page <= MAX_SECRET_PAGES; page++) {
                    JsonObject result = GitHubApi.call(service().getSecrets(owner, repo, PER_PAGE, page));
                    JsonElement secrets = result == null ? null : result.get("secrets");
                    if (secrets == null || !secrets.isJsonArray() || secrets.getAsJsonArray().size() == 0) break;
                    for (JsonElement element : secrets.getAsJsonArray()) {
                        if (element.isJsonObject()) items.add(element.getAsJsonObject());
                    }
                    if (secrets.getAsJsonArray().size() < PER_PAGE) break;
                }
                return items;
        }
        if (array != null) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) items.add(element.getAsJsonObject());
            }
        }
        return items;
    }

    private void render(@NonNull List<JsonObject> items) {
        itemList.removeAllViews();
        turnOnBn.setVisibility(View.GONE);
        footerText.setVisibility(tab != TAB_SECRETS && items.size() >= PER_PAGE ? View.VISIBLE : View.GONE);
        footerText.setText(R.string.security_first_100);
        if (items.isEmpty()) {
            emptyBox.setVisibility(View.VISIBLE);
            emptyText.setText(tab == TAB_SECRETS ? R.string.secrets_none
                    : showClosed ? R.string.security_no_closed_alerts : R.string.security_no_open_alerts);
        } else {
            emptyBox.setVisibility(View.GONE);
        }
        if (tab == TAB_SECRETS) {
            secretNames.clear();
            for (JsonObject secret : items) {
                secretNames.add(Json.str(secret, "name"));
                itemList.addView(secretRow(secret));
            }
            return;
        }
        for (JsonObject alert : items) itemList.addView(alertRow(alert));
    }

    /** Scanner off, no access or another problem: say which, and offer to turn it on. */
    private void showError(@NonNull Throwable error) {
        itemList.removeAllViews();
        footerText.setVisibility(View.GONE);
        emptyBox.setVisibility(View.VISIBLE);
        turnOnBn.setVisibility(View.GONE);
        String message = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
        int code = error instanceof GitHubApi.ApiException ? ((GitHubApi.ApiException) error).code : 0;
        String lower = message.toLowerCase(Locale.ROOT);
        boolean off = (code == 403 || code == 404) && (lower.contains("disabled")
                || lower.contains("not enabled") || lower.contains("no analysis"));
        @StringRes int intro = 0;
        if (off) {
            switch (tab) {
                case TAB_DEPENDABOT:
                    intro = R.string.security_dependabot_off;
                    break;
                case TAB_CODE_SCANNING:
                    // private repositories without Advanced Security cannot turn it on here
                    if (!lower.contains("advanced security")) intro = R.string.security_code_scanning_off;
                    break;
                case TAB_SECRET_SCANNING:
                    intro = R.string.security_secret_scanning_off;
                    break;
            }
        } else if (code == 403 || code == 404) {
            intro = R.string.security_no_access;
        }
        StringBuilder text = new StringBuilder();
        if (intro != 0) {
            text.append(getString(intro));
            if (off && !admin) text.append('\n').append(getString(R.string.security_admin_turns_on));
            text.append("\n\n");
        }
        text.append('(').append(message).append(')');
        emptyText.setText(text);
        if (off && admin && intro != 0) {
            turnOnBn.setText(tab == TAB_CODE_SCANNING ? R.string.security_set_up_codeql : R.string.security_turn_on);
            turnOnBn.setVisibility(View.VISIBLE);
        }
    }

    private void turnOn() {
        final int forTab = tab;
        run(() -> {
            switch (forTab) {
                case TAB_DEPENDABOT:
                    GitHubApi.call(service().enableVulnerabilityAlerts(owner, repo));
                    break;
                case TAB_CODE_SCANNING: {
                    JsonObject body = new JsonObject();
                    body.addProperty("state", "configured");
                    GitHubApi.call(service().updateCodeScanningSetup(owner, repo, GistActivity.json(body)));
                    break;
                }
                default: {
                    JsonObject status = new JsonObject();
                    status.addProperty("status", "enabled");
                    JsonObject analysis = new JsonObject();
                    analysis.add("secret_scanning", status);
                    JsonObject body = new JsonObject();
                    body.add("security_and_analysis", analysis);
                    GitHubApi.call(service().updateRepo(owner, repo, GistActivity.json(body)));
                    break;
                }
            }
            return true;
        }, getString(forTab == TAB_CODE_SCANNING ? R.string.security_codeql_started : R.string.security_turned_on));
    }

    // ---------------------------------------------------------------------------------
    // Alerts
    // ---------------------------------------------------------------------------------

    @NonNull
    private View alertRow(@NonNull JsonObject alert) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_alert, itemList, false);
        TextView severityView = row.findViewById(R.id.alert_severity);
        String severity = severity(alert);
        if (severity.isEmpty()) {
            severityView.setVisibility(View.GONE);
        } else {
            severityView.setText(severity);
            severityView.setTextColor(severityColor(severity));
        }
        ((TextView) row.findViewById(R.id.alert_title)).setText(title(alert));

        List<String> meta = new ArrayList<>();
        meta.add("#" + Json.integer(alert, "number"));
        switch (tab) {
            case TAB_DEPENDABOT:
                meta.add(Json.str(alert, "dependency.package.name")
                        + " (" + Json.str(alert, "dependency.package.ecosystem") + ")");
                addIfSet(meta, Json.str(alert, "dependency.manifest_path"));
                break;
            case TAB_CODE_SCANNING:
                addIfSet(meta, location(alert));
                addIfSet(meta, Json.str(alert, "tool.name"));
                break;
        }
        Date created = Json.date(alert, "created_at");
        if (created != null) meta.add(StringUtils.getNewsTimeStr(this, created));
        String state = stateLabel(alert);
        if (state != null) meta.add(state);
        ((TextView) row.findViewById(R.id.alert_meta)).setText(String.join(" · ", meta));
        row.setOnClickListener(v -> showAlert(alert));
        return row;
    }

    private static void addIfSet(@NonNull List<String> list, @Nullable String value) {
        if (!StringUtils.isBlank(value)) list.add(value);
    }

    @NonNull
    private String severity(@NonNull JsonObject alert) {
        switch (tab) {
            case TAB_DEPENDABOT: {
                String value = Json.str(alert, "security_advisory.severity");
                return value.isEmpty() ? Json.str(alert, "security_vulnerability.severity") : value;
            }
            case TAB_CODE_SCANNING: {
                String value = Json.str(alert, "rule.security_severity_level");
                return value.isEmpty() ? Json.str(alert, "rule.severity") : value;
            }
            default:
                // secret scanning: whether the secret still works, when GitHub could check
                String validity = Json.str(alert, "validity");
                return "active".equals(validity) ? "active" : "";
        }
    }

    private int severityColor(@NonNull String severity) {
        switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical":
            case "active":
                return COLOR_CRITICAL;
            case "high":
            case "error":
                return COLOR_HIGH;
            case "medium":
            case "moderate":
            case "warning":
                return COLOR_MEDIUM;
            default:
                return ViewUtils.getSecondaryTextColor(this);
        }
    }

    @NonNull
    private String title(@NonNull JsonObject alert) {
        switch (tab) {
            case TAB_DEPENDABOT:
                return Json.str(alert, "security_advisory.summary");
            case TAB_CODE_SCANNING: {
                String value = Json.str(alert, "rule.description");
                if (value.isEmpty()) value = Json.str(alert, "rule.name");
                return value.isEmpty() ? Json.str(alert, "most_recent_instance.message.text") : value;
            }
            default: {
                String value = Json.str(alert, "secret_type_display_name");
                return value.isEmpty() ? Json.str(alert, "secret_type") : value;
            }
        }
    }

    @Nullable
    private static String location(@NonNull JsonObject alert) {
        String path = Json.str(alert, "most_recent_instance.location.path");
        if (path.isEmpty()) return null;
        int line = Json.integer(alert, "most_recent_instance.location.start_line");
        return line > 0 ? path + ":" + line : path;
    }

    /** Null for open alerts. */
    @Nullable
    private String stateLabel(@NonNull JsonObject alert) {
        switch (Json.str(alert, "state")) {
            case "open":
                return null;
            case "fixed":
                return getString(R.string.security_state_fixed);
            case "resolved":
                return getString(R.string.security_state_resolved);
            default:
                return getString(R.string.security_state_dismissed);
        }
    }

    private void showAlert(@NonNull JsonObject alert) {
        if (tab == TAB_SECRET_SCANNING) {
            int number = Json.integer(alert, "number");
            showProgressDialog(getString(R.string.please_wait));
            background(() -> {
                JsonArray locations = GitHubApi.call(service().getSecretScanningLocations(owner, repo, number));
                return locations == null ? new JsonArray() : locations;
            }, locations -> {
                dismissProgressDialog();
                showAlertDialog(alert, locations);
            }, error -> {
                dismissProgressDialog();
                showAlertDialog(alert, null);
            });
        } else {
            showAlertDialog(alert, null);
        }
    }

    private void showAlertDialog(@NonNull JsonObject alert, @Nullable JsonArray locations) {
        final int forTab = tab;
        List<String> lines = new ArrayList<>();
        String severity = severity(alert);
        switch (forTab) {
            case TAB_DEPENDABOT: {
                if (!severity.isEmpty()) lines.add(getString(R.string.security_severity, severity));
                lines.add(getString(R.string.security_package, Json.str(alert, "dependency.package.ecosystem")
                        + " / " + Json.str(alert, "dependency.package.name")));
                String manifest = Json.str(alert, "dependency.manifest_path");
                if (!manifest.isEmpty()) lines.add(getString(R.string.security_manifest, manifest));
                String range = Json.str(alert, "security_vulnerability.vulnerable_version_range");
                if (!range.isEmpty()) lines.add(getString(R.string.security_vulnerable, range));
                String patched = Json.str(alert, "security_vulnerability.first_patched_version.identifier");
                lines.add(patched.isEmpty() ? getString(R.string.security_no_patch)
                        : getString(R.string.security_patched, patched));
                List<String> ids = new ArrayList<>();
                addIfSet(ids, Json.str(alert, "security_advisory.cve_id"));
                addIfSet(ids, Json.str(alert, "security_advisory.ghsa_id"));
                if (!ids.isEmpty()) lines.add(String.join(" · ", ids));
                break;
            }
            case TAB_CODE_SCANNING: {
                if (!severity.isEmpty()) lines.add(getString(R.string.security_severity, severity));
                String rule = Json.str(alert, "rule.id");
                if (!rule.isEmpty()) lines.add(getString(R.string.security_rule, rule));
                String tool = Json.str(alert, "tool.name");
                if (!tool.isEmpty()) lines.add(getString(R.string.security_tool, tool));
                String location = location(alert);
                if (location != null) lines.add(getString(R.string.security_location, location));
                String ref = Json.str(alert, "most_recent_instance.ref").replaceFirst("^refs/heads/", "");
                if (!ref.isEmpty()) lines.add(getString(R.string.security_branch, ref));
                break;
            }
            default: {
                lines.add(getString(R.string.security_secret_type, title(alert)));
                String validity = Json.str(alert, "validity");
                if (!validity.isEmpty()) lines.add(getString(R.string.security_validity, validity));
                if (Json.bool(alert, "push_protection_bypassed")) lines.add(getString(R.string.security_push_bypassed));
                if (locations != null && locations.size() > 0) {
                    lines.add(getString(R.string.security_found_in));
                    for (JsonElement element : locations) {
                        if (!element.isJsonObject()) continue;
                        JsonObject place = element.getAsJsonObject();
                        String path = Json.str(place, "details.path");
                        if (path.isEmpty()) {
                            lines.add("• " + Json.str(place, "type"));
                        } else {
                            int line = Json.integer(place, "details.start_line");
                            String sha = Json.str(place, "details.commit_sha");
                            lines.add("• " + path + (line > 0 ? ":" + line : "")
                                    + (sha.length() >= 7 ? " (" + sha.substring(0, 7) + ")" : ""));
                        }
                    }
                }
                lines.add(getString(R.string.security_value_hidden));
                break;
            }
        }
        Date created = Json.date(alert, "created_at");
        if (created != null) lines.add(getString(R.string.security_opened, StringUtils.getNewsTimeStr(this, created)));
        String state = stateLabel(alert);
        if (state != null) lines.add(state);
        String reason = forTab == TAB_SECRET_SCANNING ? Json.str(alert, "resolution") : Json.str(alert, "dismissed_reason");
        if (!reason.isEmpty()) lines.add(getString(R.string.security_reason, reason.replace('_', ' ')));
        String comment = forTab == TAB_SECRET_SCANNING ? Json.str(alert, "resolution_comment") : Json.str(alert, "dismissed_comment");
        if (!comment.isEmpty()) lines.add("\"" + comment + "\"");

        StringBuilder message = new StringBuilder(String.join("\n", lines));
        if (forTab == TAB_DEPENDABOT) {
            String description = Json.str(alert, "security_advisory.description").trim();
            if (description.length() > 1500) description = description.substring(0, 1500) + "…";
            if (!description.isEmpty()) message.append("\n\n").append(description);
        } else if (forTab == TAB_CODE_SCANNING) {
            String text = Json.str(alert, "most_recent_instance.message.text").trim();
            if (!text.isEmpty()) message.append("\n\n").append(text);
        }

        String alertState = Json.str(alert, "state");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title(alert))
                .setMessage(message)
                .setPositiveButton(R.string.open_in_browser, (d, w) ->
                        AppOpener.openInCustomTabsOrBrowser(this, Json.str(alert, "html_url")))
                .setNegativeButton(R.string.close, null);
        int number = Json.integer(alert, "number");
        if ("open".equals(alertState)) {
            builder.setNeutralButton(forTab == TAB_SECRET_SCANNING ? R.string.security_resolve : R.string.security_dismiss,
                    (d, w) -> chooseReason(forTab, number));
        } else if ("dismissed".equals(alertState) || "resolved".equals(alertState)) {
            builder.setNeutralButton(R.string.security_reopen, (d, w) -> reopen(forTab, number));
        }
        builder.show();
    }

    private void chooseReason(int forTab, int number) {
        final String[] values;
        final int[] labels;
        switch (forTab) {
            case TAB_DEPENDABOT:
                values = new String[]{"fix_started", "no_bandwidth", "tolerable_risk", "inaccurate", "not_used"};
                labels = new int[]{R.string.security_reason_fix_started, R.string.security_reason_no_bandwidth,
                        R.string.security_reason_tolerable_risk, R.string.security_reason_inaccurate,
                        R.string.security_reason_not_used};
                break;
            case TAB_CODE_SCANNING:
                values = new String[]{"false positive", "won't fix", "used in tests"};
                labels = new int[]{R.string.security_reason_false_positive, R.string.security_reason_wont_fix,
                        R.string.security_reason_used_in_tests};
                break;
            default:
                values = new String[]{"revoked", "false_positive", "wont_fix", "used_in_tests"};
                labels = new int[]{R.string.security_reason_revoked, R.string.security_reason_false_positive,
                        R.string.security_reason_wont_fix, R.string.security_reason_used_in_tests};
                break;
        }
        String[] texts = new String[labels.length];
        for (int i = 0; i < labels.length; i++) texts[i] = getString(labels[i]);
        new AlertDialog.Builder(this)
                .setTitle(forTab == TAB_SECRET_SCANNING ? R.string.security_resolve : R.string.security_dismiss)
                .setItems(texts, (d, which) -> TextInputDialog.show(this, R.string.security_comment,
                        R.string.security_comment, "", false, true,
                        comment -> close(forTab, number, values[which], comment.trim())))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void close(int forTab, int number, @NonNull String reason, @NonNull String comment) {
        JsonObject body = new JsonObject();
        if (forTab == TAB_SECRET_SCANNING) {
            body.addProperty("state", "resolved");
            body.addProperty("resolution", reason);
            if (!comment.isEmpty()) body.addProperty("resolution_comment", comment);
        } else {
            body.addProperty("state", "dismissed");
            body.addProperty("dismissed_reason", reason);
            if (!comment.isEmpty()) body.addProperty("dismissed_comment", comment);
        }
        updateAlert(forTab, number, body, getString(forTab == TAB_SECRET_SCANNING
                ? R.string.security_resolved : R.string.security_dismissed));
    }

    private void reopen(int forTab, int number) {
        JsonObject body = new JsonObject();
        body.addProperty("state", "open");
        updateAlert(forTab, number, body, getString(R.string.security_reopened));
    }

    private void updateAlert(int forTab, int number, @NonNull JsonObject body, @NonNull String done) {
        run(() -> {
            switch (forTab) {
                case TAB_DEPENDABOT:
                    GitHubApi.call(service().updateDependabotAlert(owner, repo, number, GistActivity.json(body)));
                    break;
                case TAB_CODE_SCANNING:
                    GitHubApi.call(service().updateCodeScanningAlert(owner, repo, number, GistActivity.json(body)));
                    break;
                default:
                    GitHubApi.call(service().updateSecretScanningAlert(owner, repo, number, GistActivity.json(body)));
                    break;
            }
            return true;
        }, done);
    }

    // ---------------------------------------------------------------------------------
    // Actions secrets
    // ---------------------------------------------------------------------------------

    @NonNull
    private View secretRow(@NonNull JsonObject secret) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_setting_row, itemList, false);
        String name = Json.str(secret, "name");
        TextView title = row.findViewById(R.id.setting_title);
        title.setText(name);
        title.setTypeface(android.graphics.Typeface.MONOSPACE);
        Date updated = Json.date(secret, "updated_at");
        ((TextView) row.findViewById(R.id.setting_summary)).setText(updated == null ? ""
                : getString(R.string.secrets_updated, StringUtils.getNewsTimeStr(this, updated)));
        row.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
            popup.getMenu().add(0, 1, 0, R.string.secrets_update);
            popup.getMenu().add(0, 2, 1, R.string.delete);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) editSecret(name);
                else deleteSecret(name);
                return true;
            });
            popup.show();
        });
        return row;
    }

    /** @param existing null: a new secret; else a new value for this one */
    private void editSecret(@Nullable String existing) {
        View view = LayoutInflater.from(new AlertDialog.Builder(this).getContext())
                .inflate(R.layout.dialog_secret, null);
        TextInputLayout nameLayout = view.findViewById(R.id.secret_name_layout);
        TextInputEditText nameEdit = view.findViewById(R.id.secret_name);
        TextInputLayout valueLayout = view.findViewById(R.id.secret_value_layout);
        TextInputEditText valueEdit = view.findViewById(R.id.secret_value);
        if (existing != null) {
            nameEdit.setText(existing);
            nameEdit.setEnabled(false);
            valueEdit.requestFocus();
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.secrets_add : R.string.secrets_update)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = SecretNames.normalize(nameEdit.getText() == null ? "" : nameEdit.getText().toString());
            String value = valueEdit.getText() == null ? "" : valueEdit.getText().toString();
            nameLayout.setError(null);
            valueLayout.setError(null);
            if (existing == null) {
                int check = SecretNames.check(name);
                if (check != SecretNames.OK) {
                    nameLayout.setError(getString(check == SecretNames.EMPTY ? R.string.field_required
                            : check == SecretNames.STARTS_WITH_DIGIT ? R.string.secrets_name_digit
                            : check == SecretNames.GITHUB_PREFIX ? R.string.secrets_name_github
                            : R.string.secrets_name_bad));
                    return;
                }
                if (secretNames.contains(name)) {
                    nameLayout.setError(getString(R.string.secrets_exists, name));
                    return;
                }
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0) {
                valueLayout.setError(getString(R.string.secrets_value_empty));
                return;
            }
            if (bytes.length > SecretNames.MAX_VALUE_BYTES) {
                valueLayout.setError(getString(R.string.secrets_value_too_big));
                return;
            }
            dialog.dismiss();
            saveSecret(existing != null ? existing : name, bytes);
        }));
        dialog.show();
    }

    private void saveSecret(@NonNull String name, @NonNull byte[] value) {
        run(() -> {
            JsonObject key = GitHubApi.call(service().getSecretsPublicKey(owner, repo));
            byte[] publicKey = Base64.getDecoder().decode(Json.str(key, "key"));
            byte[] sealed = SealedBox.seal(value, publicKey);
            java.util.Arrays.fill(value, (byte) 0);
            JsonObject body = new JsonObject();
            body.addProperty("encrypted_value", Base64.getEncoder().encodeToString(sealed));
            body.addProperty("key_id", Json.str(key, "key_id"));
            GitHubApi.call(service().putSecret(owner, repo, name, GistActivity.json(body)));
            return true;
        }, getString(R.string.secrets_saved, name));
    }

    private void deleteSecret(@NonNull String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.secrets_delete_message, name))
                .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                    GitHubApi.call(service().deleteSecret(owner, repo, name));
                    return true;
                }, getString(R.string.secrets_deleted, name)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------

    /** Runs a change, then reloads the tab. */
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
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        tabLayout = root.findViewById(R.id.tab_layout);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        emptyBox = root.findViewById(R.id.empty_box);
        emptyText = root.findViewById(R.id.empty_text);
        turnOnBn = root.findViewById(R.id.turn_on_bn);
        turnOnBn.setOnClickListener(v -> turnOn());
        itemList = root.findViewById(R.id.item_list);
        footerText = root.findViewById(R.id.footer_text);
        addSecretBn = root.findViewById(R.id.add_secret_bn);
    }
}
