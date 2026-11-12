package com.thirtydegreesray.openhub.ui.widget;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.IssueFieldService;
import com.thirtydegreesray.openhub.http.IssueService;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.GraphQl;
import com.thirtydegreesray.openhub.util.IssueFields;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * The issue type and the issue field values of an issue (both defined by the organization
 * that owns the repository), shown at the top of the issue timeline. People with push access
 * tap a row to change it. Everything shown is what GitHub answered; after a change the issue
 * loads again.
 */
public class IssueFieldsBox {

    public interface Host {
        /** Asked to show the box again (the timeline's first item). */
        void issueFieldsChanged();

        /** Loads the issue again from the network. */
        void reloadIssueFromNetwork();
    }

    private final BaseActivity<?> activity;
    private final Host host;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private String owner = "";
    private String repo = "";
    private int number;
    @Nullable private Issue.IssueType type;
    @Nullable private List<Issue.FieldValue> values;

    private boolean canWrite;
    private boolean permissionsKnown;
    /** The organization's types and fields; null until GitHub answered (writers only). */
    @Nullable private List<IssueFields.Type> types;
    @Nullable private List<IssueFields.Field> fields;

    public IssueFieldsBox(@NonNull BaseActivity<?> activity, @NonNull Host host) {
        this.activity = activity;
        this.host = host;
    }

    public void destroy() {
        disposables.clear();
    }

    /** The issue was (re)loaded: show its type and values, read what may be set (push access). */
    public void load(@NonNull Issue issue) {
        if (issue.getRepoAuthorName() == null || issue.getRepoName() == null) return;
        boolean sameRepo = owner.equalsIgnoreCase(issue.getRepoAuthorName())
                && repo.equalsIgnoreCase(issue.getRepoName());
        if (!sameRepo) {
            permissionsKnown = false;
            types = null;
            fields = null;
        }
        owner = issue.getRepoAuthorName();
        repo = issue.getRepoName();
        number = issue.getNumber();
        type = issue.getType();
        values = issue.getFieldValues();
        host.issueFieldsChanged();

        final String o = owner, r = repo;
        final int n = number;
        final boolean readPermissions = !permissionsKnown;
        final boolean knewWrite = canWrite;
        final boolean readDefinitions = types == null || fields == null;
        disposables.add(Single.fromCallable(() -> {
            boolean write = knewWrite;
            Boolean known = null;
            if (readPermissions) {
                try {
                    JsonObject json = GitHubApi.call(GitHubApi.service(IssueService.class).getRepoJson(o, r));
                    write = Json.bool(json, "permissions.admin") || Json.bool(json, "permissions.maintain")
                            || Json.bool(json, "permissions.push");
                    known = write;
                } catch (Exception ignored) {
                    write = false;
                }
            }
            List<IssueFields.Type> t = null;
            List<IssueFields.Field> f = null;
            List<Issue.FieldValue> v = null;
            if (write) {
                if (readDefinitions) {
                    try {
                        t = IssueFields.types(GitHubApi.call(api().getIssueTypes(o, r)));
                    } catch (Exception e) {
                        t = new ArrayList<>();
                    }
                    try {
                        f = IssueFields.fields(GraphQl.run(IssueFields.FIELDS_QUERY,
                                GraphQl.vars("owner", o, "repo", r)));
                    } catch (Exception e) {
                        f = new ArrayList<>();
                    }
                }
                // the values as GitHub has them now (the issue may come from the cache)
                try {
                    JsonArray array = GitHubApi.call(api().getFieldValues(o, r, n));
                    if (array != null) {
                        Issue.FieldValue[] read = new GsonBuilder().create().fromJson(array, Issue.FieldValue[].class);
                        v = read == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(read));
                    }
                } catch (Exception ignored) {
                }
            }
            return new Object[]{known, t, f, v};
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result[0] != null) {
                        canWrite = (Boolean) result[0];
                        permissionsKnown = true;
                    }
                    if (result[1] != null) types = cast(result[1]);
                    if (result[2] != null) fields = cast(result[2]);
                    if (result[3] != null && n == number) values = cast(result[3]);
                    host.issueFieldsChanged();
                }, error -> {
                    // the issue itself is still shown; the box stays as it was
                }));
    }

    // ---------------------------------------------------------------- showing

    private boolean writable() {
        return canWrite && ((types != null && !types.isEmpty()) || (fields != null && !fields.isEmpty()));
    }

    /** Fills {@code container} (in the first timeline item) with the type and the field values. */
    public void bind(@NonNull LinearLayout container) {
        container.removeAllViews();
        Context ctx = container.getContext();
        List<Issue.FieldValue> shownValues = new ArrayList<>();
        if (values != null) {
            for (Issue.FieldValue v : values) if (IssueFields.hasValue(v)) shownValues.add(v);
        }
        boolean hasType = type != null && !StringUtils.isBlank(type.name);
        boolean write = writable();
        if (!hasType && shownValues.isEmpty() && !write) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_box);
        box.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.topMargin = dp(ctx, 8);
        container.addView(box, boxParams);

        boolean anyFields = !shownValues.isEmpty() || (write && fields != null && !fields.isEmpty());
        box.addView(heading(ctx, ctx.getString(anyFields ? R.string.issue_fields_title : R.string.issue_type_title)));

        boolean typeWritable = write && types != null && !types.isEmpty();
        if (hasType || typeWritable) {
            CharSequence value = hasType ? dotted(type.name, type.color) : none(ctx);
            box.addView(row(ctx, ctx.getString(R.string.issue_type_label), value,
                    typeWritable ? v -> pickType() : null));
        }

        List<Long> listed = new ArrayList<>();
        if (write && fields != null) {
            for (IssueFields.Field f : fields) {
                listed.add(f.id);
                Issue.FieldValue v = IssueFields.valueOf(values, f.id);
                CharSequence text = IssueFields.hasValue(v) ? valueText(v) : none(ctx);
                box.addView(row(ctx, f.name, text, view -> editField(f, v)));
            }
        }
        for (Issue.FieldValue v : shownValues) {
            if (listed.contains(v.fieldId)) continue;
            String name = StringUtils.isBlank(v.fieldName) ? "#" + v.fieldId : v.fieldName;
            box.addView(row(ctx, name, valueText(v), null));
        }
        if (write) {
            TextView hint = new TextView(ctx);
            hint.setText(R.string.issue_fields_hint);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
            hint.setTextColor(secondary(ctx));
            int pad = dp(ctx, 12);
            hint.setPadding(pad, dp(ctx, 4), pad, dp(ctx, 2));
            box.addView(hint);
        }
    }

    /** "● High" with the option color, or the plain text. */
    private CharSequence valueText(@NonNull Issue.FieldValue v) {
        String type = IssueFields.dataType(v.dataType);
        if (IssueFields.SINGLE_SELECT.equals(type) && v.singleSelectOption != null
                && !StringUtils.isBlank(v.singleSelectOption.name)) {
            return dotted(v.singleSelectOption.name, v.singleSelectOption.color);
        }
        if (IssueFields.MULTI_SELECT.equals(type) && v.multiSelectOptions != null && !v.multiSelectOptions.isEmpty()) {
            SpannableStringBuilder text = new SpannableStringBuilder();
            for (Issue.FieldOption o : v.multiSelectOptions) {
                if (o == null || StringUtils.isBlank(o.name)) continue;
                if (text.length() > 0) text.append("   ");
                text.append(dotted(o.name, o.color));
            }
            return text;
        }
        return IssueFields.display(v);
    }

    /** A colored dot before the name, like the colors on github.com. */
    public static CharSequence dotted(@NonNull String name, @Nullable String color) {
        SpannableStringBuilder text = new SpannableStringBuilder("● ");
        text.setSpan(new ForegroundColorSpan(IssueFields.color(color)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(name);
        return text;
    }

    private CharSequence none(Context ctx) {
        SpannableStringBuilder text = new SpannableStringBuilder(ctx.getString(R.string.issue_field_none));
        text.setSpan(new ForegroundColorSpan(secondary(ctx)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private TextView heading(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(secondary(ctx));
        int pad = dp(ctx, 12);
        view.setPadding(pad, 0, pad, dp(ctx, 4));
        return view;
    }

    private View row(Context ctx, String label, CharSequence value, @Nullable View.OnClickListener click) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(ctx, 12);
        row.setPadding(pad, dp(ctx, 6), pad, dp(ctx, 6));

        TextView name = new TextView(ctx);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
        name.setTextColor(secondary(ctx));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));

        TextView text = new TextView(ctx);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_normal));
        text.setTextColor(primary(ctx));
        text.setMaxLines(4);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 3f);
        textParams.setMarginStart(dp(ctx, 8));
        row.addView(text, textParams);

        if (click != null) {
            TypedValue ripple = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            row.setOnClickListener(click);
        }
        return row;
    }

    // ---------------------------------------------------------------- the type

    private void pickType() {
        if (types == null || types.isEmpty()) return;
        String[] names = new String[types.size() + 1];
        names[0] = activity.getString(R.string.issue_type_none);
        int checked = 0;
        for (int i = 0; i < types.size(); i++) {
            IssueFields.Type t = types.get(i);
            names[i + 1] = t.name;
            if (type != null && t.name.equalsIgnoreCase(type.name)) checked = i + 1;
        }
        final int current = checked;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.issue_type_pick)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == current) return;
                    setType(which == 0 ? null : types.get(which - 1).name);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setType(@Nullable String name) {
        background(() -> {
            JsonObject body = new JsonObject();
            body.add("type", name == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(name));
            JsonObject answer = GitHubApi.call(api().editIssue(owner, repo, number, json(body)));
            String now = Json.str(answer, "type.name");
            // GitHub drops a type change silently without push access: say what it really did
            return name == null ? StringUtils.isBlank(now) : name.equalsIgnoreCase(now);
        }, changed -> {
            if (changed) {
                activity.showSuccessToast(name == null ? activity.getString(R.string.issue_type_removed)
                        : activity.getString(R.string.issue_type_set, name));
            } else {
                activity.showErrorToast(activity.getString(R.string.issue_type_not_changed));
            }
            reload();
        });
    }

    // ---------------------------------------------------------------- the fields

    private void editField(@NonNull IssueFields.Field f, @Nullable Issue.FieldValue v) {
        String type = IssueFields.dataType(f.dataType);
        boolean has = IssueFields.hasValue(v);
        switch (type) {
            case IssueFields.SINGLE_SELECT:
                editSingle(f, v);
                break;
            case IssueFields.MULTI_SELECT:
                editMulti(f, v, has);
                break;
            case IssueFields.DATE:
                editDate(f, v, has);
                break;
            default:
                editText(f, v, has, IssueFields.NUMBER.equals(type));
                break;
        }
    }

    private void editText(IssueFields.Field f, @Nullable Issue.FieldValue v, boolean has, boolean number) {
        Context ctx = new AlertDialog.Builder(activity).getContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        layout.setPadding(pad, dp(ctx, 8), pad, 0);
        if (!StringUtils.isBlank(f.description)) {
            TextView about = new TextView(ctx);
            about.setText(f.description);
            about.setTextColor(secondary(ctx));
            about.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
            layout.addView(about);
        }
        EditText input = new com.thirtydegreesray.openhub.ui.widget.OhEditText(ctx);
        input.setText(v == null ? "" : number ? IssueFields.number(IssueFields.raw(v.value)) : IssueFields.raw(v.value));
        input.setSelection(input.getText().length());
        if (number) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
            input.setSingleLine(true);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMaxLines(6);
        }
        layout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(f.name)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        if (has) builder.setNeutralButton(R.string.issue_field_remove, (d, w) -> removeValue(f));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String text = input.getText().toString();
            if (text.trim().isEmpty()) {
                dialog.dismiss();
                if (has) removeValue(f);
                return;
            }
            JsonElement value = IssueFields.value(f.dataType, text);
            if (value == null) {
                input.setError(activity.getString(R.string.issue_field_not_number));
                return;
            }
            dialog.dismiss();
            setValue(f, value);
        }));
        dialog.show();
    }

    private void editDate(IssueFields.Field f, @Nullable Issue.FieldValue v, boolean has) {
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date current = v == null ? null : IssueFields.parseDay(IssueFields.raw(v.value));
        if (current != null) {
            day.setTime(current);
        } else {
            Calendar local = Calendar.getInstance();
            day.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        }
        DatePickerDialog dialog = new DatePickerDialog(new AlertDialog.Builder(activity).getContext(),
                (picker, y, m, d) -> setValue(f, new com.google.gson.JsonPrimitive(IssueFields.day(y, m, d))),
                day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle(f.name);
        if (has) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.issue_field_remove),
                    (d, w) -> removeValue(f));
        }
        dialog.show();
    }

    private void editSingle(IssueFields.Field f, @Nullable Issue.FieldValue v) {
        if (f.options.isEmpty()) return;
        List<String> picked = IssueFields.picked(v);
        String[] names = new String[f.options.size() + 1];
        names[0] = activity.getString(R.string.issue_field_none);
        int checked = 0;
        for (int i = 0; i < f.options.size(); i++) {
            names[i + 1] = f.options.get(i).name;
            if (!picked.isEmpty() && picked.get(0).equals(f.options.get(i).name)) checked = i + 1;
        }
        final int current = checked;
        new AlertDialog.Builder(activity)
                .setTitle(f.name)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == current) return;
                    if (which == 0) removeValue(f);
                    else setValue(f, new com.google.gson.JsonPrimitive(f.options.get(which - 1).name));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editMulti(IssueFields.Field f, @Nullable Issue.FieldValue v, boolean has) {
        if (f.options.isEmpty()) return;
        List<String> picked = IssueFields.picked(v);
        String[] names = new String[f.options.size()];
        boolean[] checked = new boolean[f.options.size()];
        for (int i = 0; i < f.options.size(); i++) {
            names[i] = f.options.get(i).name;
            checked[i] = picked.contains(names[i]);
        }
        new AlertDialog.Builder(activity)
                .setTitle(f.name)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < names.length; i++) if (checked[i]) chosen.add(names[i]);
                    if (chosen.equals(picked)) return;
                    JsonElement value = IssueFields.value(chosen);
                    if (value == null) {
                        if (has) removeValue(f);
                    } else {
                        setValue(f, value);
                    }
                })
                .show();
    }

    private void setValue(IssueFields.Field f, JsonElement value) {
        background(() -> {
            GitHubApi.call(api().addFieldValues(owner, repo, number, IssueFields.body(f.id, value)));
            return Boolean.TRUE;
        }, ok -> {
            activity.showSuccessToast(activity.getString(R.string.issue_field_saved, f.name));
            reload();
        });
    }

    private void removeValue(IssueFields.Field f) {
        background(() -> {
            try {
                GitHubApi.call(api().removeFieldValue(owner, repo, number, f.id));
            } catch (GitHubApi.ApiException e) {
                // 404: the issue has no value for this field (any more)
                if (e.code != 404) throw e;
            }
            return Boolean.TRUE;
        }, ok -> {
            activity.showSuccessToast(activity.getString(R.string.issue_field_removed, f.name));
            reload();
        });
    }

    // ---------------------------------------------------------------- small helpers

    private void reload() {
        SubIssuesBox.markChanged();
        PullRefresh.freshFromNow();
        host.reloadIssueFromNetwork();
    }

    private <T> void background(Callable<T> work, Consumer<T> onSuccess) {
        activity.showProgressDialog(activity.getString(R.string.please_wait));
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    activity.dismissProgressDialog();
                    onSuccess.accept(result);
                }, error -> {
                    activity.dismissProgressDialog();
                    activity.showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                }));
    }

    private static RequestBody json(JsonObject body) {
        return RequestBody.create(new GsonBuilder().serializeNulls().create().toJson(body),
                MediaType.parse("application/json; charset=utf-8"));
    }

    private static IssueFieldService api() {
        return GitHubApi.service(IssueFieldService.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(Object value) {
        return (List<T>) value;
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int primary(Context ctx) {
        android.content.res.TypedArray array = ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color = array.getColor(0, 0xFFE2E8EE);
        array.recycle();
        return color;
    }

    private static int secondary(Context ctx) {
        android.content.res.TypedArray array = ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        int color = array.getColor(0, 0xFF8B949E);
        array.recycle();
        return color;
    }
}
