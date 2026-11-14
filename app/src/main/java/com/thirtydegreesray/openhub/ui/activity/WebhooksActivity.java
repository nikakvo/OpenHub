package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.WebhookService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ErrorTexts;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.Webhooks;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Webhooks of a repository, like github.com/owner/repo/settings/hooks (admins): where
 * GitHub sends events, whether it is active and how the last delivery went. A webhook can
 * be turned off and on, pinged and deleted. With a hook id the screen shows the recent
 * deliveries of that webhook instead: their result, details and Redeliver.
 * Every state shown is GitHub's answer; after a change it is read again.
 */
public class WebhooksActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, WebhooksActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    @NonNull
    private static Intent deliveriesIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                           long hookId, @NonNull String hookUrl) {
        return createIntent(context, owner, repo).putExtra("hookId", hookId).putExtra("hookUrl", hookUrl);
    }

    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 5;
    private static final int MAX_PAYLOAD = 20000;
    /** GitHub needs a moment before a redelivery shows up in the list. */
    private static final long REDELIVERY_RELOAD_MS = 3000;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_BROWSER = 2;

    @AutoAccess String owner;
    @AutoAccess String repo;
    /** > 0: the deliveries of this webhook */
    @AutoAccess long hookId;
    @AutoAccess String hookUrl;

    private LinearLayout list;
    private TextView emptyText;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_webhooks;
    }

    private boolean showsDeliveries() {
        return hookId > 0;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        if (showsDeliveries()) {
            setToolbarTitle(getString(R.string.webhooks_deliveries), Webhooks.host(hookUrl));
        } else {
            setToolbarTitle(getString(R.string.webhooks), owner + "/" + repo);
        }
        PullRefresh.wrap(findViewById(R.id.hooks_scroll), this::reload).follow(loader);
        reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private WebhookService service() {
        return GitHubApi.service(WebhookService.class);
    }

    private void reload() {
        if (showsDeliveries()) loadDeliveries();
        else loadHooks();
    }

    // ---------------------------------------------------------------------------------
    // Webhooks
    // ---------------------------------------------------------------------------------

    private void loadHooks() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        background(() -> {
            List<JsonObject> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonArray items = GitHubApi.call(service().getHooks(owner, repo, PER_PAGE, page));
                int count = 0;
                if (items != null) {
                    for (JsonElement e : items) {
                        if (!e.isJsonObject()) continue;
                        all.add(e.getAsJsonObject());
                        count++;
                    }
                }
                if (count < PER_PAGE) break;
            }
            return all;
        }, (List<JsonObject> hooks) -> {
            loader.setVisibility(View.GONE);
            list.removeAllViews();
            for (JsonObject hook : hooks) list.addView(hookRow(hook));
            if (hooks.isEmpty()) {
                emptyText.setText(R.string.webhooks_none);
                emptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    @NonNull
    private static String hookUrlOf(@NonNull JsonObject hook) {
        String url = Json.str(hook, "config.url");
        return url.isEmpty() ? "#" + Json.lng(hook, "id") : url;
    }

    @NonNull
    private static List<String> strings(@Nullable JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return out;
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive()) out.add(e.getAsString());
        }
        return out;
    }

    @NonNull
    private String when(@Nullable Date date) {
        return date == null ? "-" : StringUtils.getNewsTimeStr(this, date);
    }

    private void appendColored(@NonNull SpannableStringBuilder text, @NonNull String part, int color) {
        int start = text.length();
        text.append(part);
        text.setSpan(new ForegroundColorSpan(color), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @NonNull
    private View hookRow(@NonNull JsonObject hook) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        TextView title = row.findViewById(R.id.entry_title);
        title.setText(hookUrlOf(hook));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        String events = Webhooks.events(strings(Json.get(hook, "events")));
        TextView detail = row.findViewById(R.id.entry_detail);
        detail.setTypeface(Typeface.DEFAULT);
        detail.setText(events == null ? getString(R.string.webhooks_no_events)
                : "*".equals(events) ? getString(R.string.webhooks_all_events) : events);

        SpannableStringBuilder state = new SpannableStringBuilder();
        boolean active = Json.bool(hook, "active");
        if (active) appendColored(state, getString(R.string.webhooks_active), ContextCompat.getColor(this, R.color.pull_open));
        else state.append(getString(R.string.webhooks_inactive));
        state.append("  ·  ");
        JsonElement codeElement = Json.get(hook, "last_response.code");
        Integer code = codeElement != null && codeElement.isJsonPrimitive() ? codeElement.getAsInt() : null;
        String status = Json.str(hook, "last_response.status");
        String message = Json.str(hook, "last_response.message");
        switch (Webhooks.lastResponse(code, status)) {
            case OK:
                state.append(getString(R.string.webhooks_last_ok, code + (message.isEmpty() ? "" : " " + message)));
                break;
            case FAILED:
                String why = !message.isEmpty() ? message : code != null ? String.valueOf(code) : status;
                appendColored(state, getString(R.string.webhooks_last_failed, why),
                        ContextCompat.getColor(this, R.color.pull_closed));
                break;
            default:
                state.append(getString(R.string.webhooks_never_delivered));
                break;
        }
        state.append("\n").append(getString(R.string.webhooks_updated, when(Json.date(hook, "updated_at"))));
        ((TextView) row.findViewById(R.id.entry_state)).setText(state);

        row.setOnClickListener(v -> showHookActions(v, hook));
        return row;
    }

    private void showHookActions(@NonNull View anchor, @NonNull JsonObject hook) {
        long id = Json.lng(hook, "id");
        String url = hookUrlOf(hook);
        boolean active = Json.bool(hook, "active");
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.webhooks_deliveries);
        popup.getMenu().add(0, 2, 1, active ? R.string.webhooks_turn_off : R.string.webhooks_turn_on);
        popup.getMenu().add(0, 3, 2, R.string.webhooks_ping);
        popup.getMenu().add(0, 4, 3, R.string.webhooks_copy_url);
        popup.getMenu().add(0, 5, 4, R.string.webhooks_delete);
        popup.getMenu().add(0, 6, 5, R.string.open_in_browser);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startActivity(deliveriesIntent(this, owner, repo, id, url));
                    return true;
                case 2:
                    if (active) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.webhooks_turn_off)
                                .setMessage(getString(R.string.webhooks_turn_off_message, Webhooks.host(url)))
                                .setPositiveButton(R.string.webhooks_turn_off, (d, w) -> setActive(id, false))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        setActive(id, true);
                    }
                    return true;
                case 3:
                    ping(id);
                    return true;
                case 4:
                    AppUtils.copyToClipboard(this, url);
                    showSuccessToast(getString(R.string.webhooks_url_copied));
                    return true;
                case 5:
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.webhooks_delete)
                            .setMessage(getString(R.string.webhooks_delete_message, url))
                            .setPositiveButton(R.string.delete, (d, w) -> deleteHook(id))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                case 6:
                    AppOpener.openInCustomTabsOrBrowser(this, Webhooks.settingsUrl(owner, repo, id));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    /** Changes active, then reads the webhook back: the toast tells GitHub's real state. */
    private void setActive(long id, boolean active) {
        showProgressDialog(getString(R.string.please_wait));
        Map<String, Object> body = new HashMap<>();
        body.put("active", active);
        work(() -> {
            GitHubApi.call(service().updateHook(owner, repo, id, body));
            return Json.bool(GitHubApi.call(service().getHook(owner, repo, id)), "active");
        }, (Boolean now) -> {
            if (now == active) {
                showSuccessToast(getString(active ? R.string.webhooks_turned_on : R.string.webhooks_turned_off));
            } else {
                showWarningToast(getString(R.string.webhooks_state_not_changed));
            }
            reload();
        });
    }

    private void ping(long id) {
        showProgressDialog(getString(R.string.please_wait));
        work(() -> {
            GitHubApi.call(service().ping(owner, repo, id));
            return true;
        }, done -> {
            showSuccessToast(getString(R.string.webhooks_pinged));
            reload();
        });
    }

    /** Deletes, then asks GitHub for the webhook: only a 404 counts as gone. */
    private void deleteHook(long id) {
        showProgressDialog(getString(R.string.please_wait));
        work(() -> {
            GitHubApi.call(service().deleteHook(owner, repo, id));
            try {
                GitHubApi.call(service().getHook(owner, repo, id));
                return false;
            } catch (GitHubApi.ApiException e) {
                if (e.code == 404) return true;
                throw e;
            }
        }, (Boolean gone) -> {
            if (gone) showSuccessToast(getString(R.string.webhooks_deleted));
            else showWarningToast(getString(R.string.webhooks_still_there));
            reload();
        });
    }

    // ---------------------------------------------------------------------------------
    // Deliveries
    // ---------------------------------------------------------------------------------

    private void loadDeliveries() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        background(() -> {
            JsonArray items = GitHubApi.call(service().getDeliveries(owner, repo, hookId, Webhooks.DELIVERIES));
            List<JsonObject> all = new ArrayList<>();
            if (items != null) {
                for (JsonElement e : items) if (e.isJsonObject()) all.add(e.getAsJsonObject());
            }
            return all;
        }, (List<JsonObject> deliveries) -> {
            loader.setVisibility(View.GONE);
            list.removeAllViews();
            for (JsonObject delivery : deliveries) list.addView(deliveryRow(delivery));
            if (deliveries.isEmpty()) {
                emptyText.setText(R.string.webhooks_deliveries_none);
                emptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    @NonNull
    private String resultText(int statusCode, @NonNull String status) {
        if (statusCode == 0) return getString(R.string.webhooks_could_not_connect);
        return status.isEmpty() || status.equals(String.valueOf(statusCode)) ? String.valueOf(statusCode)
                : statusCode + " " + status;
    }

    @NonNull
    private View deliveryRow(@NonNull JsonObject delivery) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        ((TextView) row.findViewById(R.id.entry_title)).setText(
                Webhooks.deliveryTitle(Json.str(delivery, "event"), Json.str(delivery, "action")));
        ((TextView) row.findViewById(R.id.entry_detail)).setText(Json.str(delivery, "guid"));

        SpannableStringBuilder state = new SpannableStringBuilder();
        int statusCode = Json.integer(delivery, "status_code");
        String result = resultText(statusCode, Json.str(delivery, "status"));
        boolean ok = Webhooks.delivery(statusCode) == Webhooks.Result.OK;
        appendColored(state, result, ContextCompat.getColor(this, ok ? R.color.pull_open : R.color.pull_closed));
        JsonElement duration = Json.get(delivery, "duration");
        if (duration != null && duration.isJsonPrimitive()) {
            state.append("  ·  ").append(Webhooks.duration(duration.getAsDouble()));
        }
        state.append("  ·  ").append(when(Json.date(delivery, "delivered_at")));
        if (Json.bool(delivery, "redelivery")) state.append("  ·  ").append(getString(R.string.webhooks_redelivery));
        ((TextView) row.findViewById(R.id.entry_state)).setText(state);

        row.setOnClickListener(v -> showDeliveryActions(v, delivery));
        return row;
    }

    private void showDeliveryActions(@NonNull View anchor, @NonNull JsonObject delivery) {
        long id = Json.lng(delivery, "id");
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.webhooks_details);
        popup.getMenu().add(0, 2, 1, R.string.webhooks_redeliver);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showDetails(id);
                return true;
            } else if (item.getItemId() == 2) {
                redeliver(id);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void redeliver(long id) {
        showProgressDialog(getString(R.string.please_wait));
        work(() -> {
            GitHubApi.call(service().redeliver(owner, repo, hookId, id));
            return true;
        }, done -> {
            showInfoToast(getString(R.string.webhooks_redelivering));
            list.postDelayed(() -> {
                if (!isFinishing()) reload();
            }, REDELIVERY_RELOAD_MS);
        });
    }

    /** The receiver's answer and the payload GitHub sent, as GitHub stored them. */
    private void showDetails(long id) {
        showProgressDialog(getString(R.string.please_wait));
        work(() -> GitHubApi.call(service().getDelivery(owner, repo, hookId, id)), (JsonObject delivery) -> {
            int statusCode = Json.integer(delivery, "status_code");
            JsonElement duration = Json.get(delivery, "duration");
            String time = duration != null && duration.isJsonPrimitive()
                    ? Webhooks.duration(duration.getAsDouble()) : "-";
            String responseBody = Json.str(delivery, "response.payload");
            JsonElement payload = Json.get(delivery, "request.payload");
            String payloadText = payload == null ? ""
                    : new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(payload);

            Context ctx = new AlertDialog.Builder(this).getContext();
            int pad = getResources().getDimensionPixelSize(R.dimen.spacing_large);
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(label(ctx, getString(R.string.webhooks_response,
                    resultText(statusCode, Json.str(delivery, "status")), time)));
            box.addView(code(ctx, responseBody.isEmpty() ? getString(R.string.webhooks_response_empty)
                    : Webhooks.cut(responseBody, MAX_PAYLOAD)));
            box.addView(label(ctx, getString(R.string.webhooks_request_payload)));
            box.addView(code(ctx, Webhooks.cut(payloadText, MAX_PAYLOAD)));
            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(box);

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(Webhooks.deliveryTitle(Json.str(delivery, "event"), Json.str(delivery, "action")))
                    .setView(scroll)
                    .setPositiveButton(R.string.close, null);
            if (!payloadText.isEmpty()) {
                builder.setNeutralButton(R.string.webhooks_copy_payload, (d, w) -> {
                    AppUtils.copyToClipboard(this, payloadText);
                    showSuccessToast(getString(R.string.webhooks_payload_copied));
                });
            }
            builder.show();
        });
    }

    @NonNull
    private TextView label(@NonNull Context ctx, @NonNull String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, getResources().getDimensionPixelSize(R.dimen.spacing_normal), 0,
                getResources().getDimensionPixelSize(R.dimen.spacing_mini));
        return view;
    }

    @NonNull
    private TextView code(@NonNull Context ctx, @NonNull String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTextIsSelectable(true);
        return view;
    }

    // ---------------------------------------------------------------------------------

    @NonNull
    private static String errorText(@NonNull Throwable error) {
        return ErrorTexts.of(error);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.refresh);
        menu.add(0, MENU_BROWSER, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            reload();
            return true;
        } else if (id == MENU_BROWSER) {
            AppOpener.openInCustomTabsOrBrowser(this, Webhooks.settingsUrl(owner, repo, hookId));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Loading: the bar at the top shows it; errors as a toast. */
    private <T> void background(@NonNull Callable<T> task, @NonNull Consumer<T> onSuccess) {
        disposables.add(Single.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, error -> {
                    loader.setVisibility(View.GONE);
                    showErrorToast(errorText(error));
                }));
    }

    /** A change behind the progress dialog; on error the list is read again. */
    private <T> void work(@NonNull Callable<T> task, @NonNull Consumer<T> onSuccess) {
        disposables.add(Single.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    dismissProgressDialog();
                    onSuccess.accept(result);
                }, error -> {
                    dismissProgressDialog();
                    showErrorToast(errorText(error));
                    reload();
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        list = root.findViewById(R.id.hooks_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
    }
}
