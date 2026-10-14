

package com.thirtydegreesray.openhub.ui.activity;

import androidx.appcompat.app.AlertDialog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.IssueService;
import com.thirtydegreesray.openhub.mvp.model.Milestone;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.util.IssueTemplates;
import com.thirtydegreesray.openhub.util.Json;
import java.util.List;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IEditIssueContract;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.Label;
import com.thirtydegreesray.openhub.mvp.presenter.EditIssuePresenter;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ChooseLabelsDialog;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;


/**
 * Created by ThirtyDegreesRay on 2017/10/13 17:37:35
 */

public class EditIssueActivity extends BaseActivity<EditIssuePresenter>
        implements IEditIssueContract.View, ChooseLabelsDialog.ChooseLabelsListener {

    TextInputEditText titleEdit;
    TextInputLayout titleLayout;
    TextInputEditText commentEdit;
    TextInputLayout commentLayout;
    TextView labelsText;
    LinearLayout editLabelsLay;
    TextView assigneesText;
    TextView milestoneText;
    View metaSection;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables =
            new io.reactivex.rxjava3.disposables.CompositeDisposable();

    public static void showForAdd(@NonNull Activity activity, @NonNull String userId,
                                  @NonNull String repoName, int requestCode) {
        Intent intent = new Intent(activity, EditIssueActivity.class);
        intent.putExtras(BundleHelper.builder().put("addMode", true).put("userId", userId)
                .put("repoName", repoName).build());
        activity.startActivityForResult(intent, requestCode);
    }

    public static void showForEdit(@NonNull Activity activity, @NonNull Issue issue, int requestCode) {
        Intent intent = new Intent(activity, EditIssueActivity.class);
        intent.putExtras(BundleHelper.builder().put("addMode", false).put("issue", issue).build());
        activity.startActivityForResult(intent, requestCode);
    }

    private final int MARKDOWN_EDITOR_REQUEST_CODE= 100;
    @AutoAccess String titleEditStr;
    @AutoAccess String commentEditStr;


    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_edit_issue;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_confirm, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_commit){
            if(checkForCommit())
                mPresenter.commitIssue(titleEdit.getText().toString(), commentEdit.getText().toString());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(mPresenter.isAddMode() ? getString(R.string.create_issue) : getString(R.string.edit_issue));
        titleEdit.setText(mPresenter.getIssueTitle());
        commentEdit.setText(mPresenter.getIssueComment());
        titleEdit.setSelection(titleEdit.length());
        commentEdit.setSelection(commentEdit.length());

        boolean isOwner = AppData.INSTANCE.getLoggedUser().getLogin().equals(mPresenter.getRepoOwner());
        if (isOwner) mPresenter.setCanEditMeta(true);
        updateMeta();
        // collaborators with triage or write access may set them too
        background(() -> GitHubApi.call(GitHubApi.service(IssueService.class)
                .getRepoJson(mPresenter.getRepoOwner(), mPresenter.getRepoName())), repo -> {
            boolean can = Json.bool(repo, "permissions.admin") || Json.bool(repo, "permissions.maintain")
                    || Json.bool(repo, "permissions.push") || Json.bool(repo, "permissions.triage");
            if (can != mPresenter.isCanEditMeta()) {
                mPresenter.setCanEditMeta(can);
                updateMeta();
            }
        }, false);

        boolean empty = StringUtils.isBlank(mPresenter.getIssueTitle()) && StringUtils.isBlank(mPresenter.getIssueComment());
        if (mPresenter.isAddMode() && savedInstanceState == null && empty) loadTemplates();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void updateMeta() {
        metaSection.setVisibility(mPresenter.isCanEditMeta() ? View.VISIBLE : View.GONE);
        setLabelsText();
        setAssigneesText();
        setMilestoneText();
    }

    private void setAssigneesText() {
        StringBuilder text = new StringBuilder();
        if (mPresenter.getIssue().getAssignees() != null) {
            for (User user : mPresenter.getIssue().getAssignees()) {
                if (text.length() > 0) text.append(", ");
                text.append(user.getLogin());
            }
        }
        assigneesText.setText(text);
    }

    private void setMilestoneText() {
        Milestone milestone = mPresenter.getIssue().getMilestone();
        milestoneText.setText(milestone == null ? "" : milestone.getTitle());
    }

    // ---------------------------------------------------------------------------------
    // Assignees and milestone
    // ---------------------------------------------------------------------------------

    private void pickAssignees() {
        background(() -> GitHubApi.call(GitHubApi.service(IssueService.class)
                .getAssignees(mPresenter.getRepoOwner(), mPresenter.getRepoName())), array -> {
            List<String> logins = new ArrayList<>();
            List<String> avatars = new ArrayList<>();
            for (JsonElement element : array) {
                logins.add(Json.str(element.getAsJsonObject(), "login"));
                avatars.add(Json.str(element.getAsJsonObject(), "avatar_url"));
            }
            if (logins.isEmpty()) {
                showInfoToast(getString(R.string.issue_assignees_empty));
                return;
            }
            boolean[] checked = new boolean[logins.size()];
            ArrayList<User> current = mPresenter.getIssue().getAssignees();
            for (int i = 0; i < logins.size(); i++) {
                if (current == null) break;
                for (User user : current) if (logins.get(i).equalsIgnoreCase(user.getLogin())) checked[i] = true;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.issue_assignees)
                    .setMultiChoiceItems(logins.toArray(new CharSequence[0]), checked,
                            (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        ArrayList<User> chosen = new ArrayList<>();
                        for (int i = 0; i < logins.size(); i++) {
                            if (!checked[i]) continue;
                            User user = new User();
                            user.setLogin(logins.get(i));
                            user.setAvatarUrl(avatars.get(i));
                            chosen.add(user);
                        }
                        if (chosen.size() > 10) {
                            showWarningToast(getString(R.string.issue_assignees_max));
                            return;
                        }
                        mPresenter.getIssue().setAssignees(chosen);
                        setAssigneesText();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }, true);
    }

    private void pickMilestone() {
        background(() -> GitHubApi.call(GitHubApi.service(IssueService.class)
                .getOpenMilestones(mPresenter.getRepoOwner(), mPresenter.getRepoName())), array -> {
            List<JsonObject> milestones = new ArrayList<>();
            for (JsonElement element : array) milestones.add(element.getAsJsonObject());
            if (milestones.isEmpty()) {
                showInfoToast(getString(R.string.issue_milestone_empty));
                return;
            }
            CharSequence[] items = new CharSequence[milestones.size() + 1];
            items[0] = getString(R.string.issue_milestone_none);
            Milestone current = mPresenter.getIssue().getMilestone();
            int selected = 0;
            for (int i = 0; i < milestones.size(); i++) {
                JsonObject m = milestones.get(i);
                items[i + 1] = Json.str(m, "title");
                if (current != null && current.getNumber() == Json.integer(m, "number")) selected = i + 1;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.issue_milestone)
                    .setSingleChoiceItems(items, selected, (d, which) -> {
                        if (which == 0) {
                            mPresenter.getIssue().setMilestone(null);
                        } else {
                            JsonObject m = milestones.get(which - 1);
                            Milestone milestone = new Milestone();
                            milestone.setNumber(Json.integer(m, "number"));
                            milestone.setTitle(Json.str(m, "title"));
                            mPresenter.getIssue().setMilestone(milestone);
                        }
                        setMilestoneText();
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }, true);
    }

    // ---------------------------------------------------------------------------------
    // Templates
    // ---------------------------------------------------------------------------------

    private void loadTemplates() {
        String owner = mPresenter.getRepoOwner();
        String repo = mPresenter.getRepoName();
        background(() -> IssueTemplates.load(owner, repo), templates -> {
            if (templates.isEmpty() || isFinishing()) return;
            CharSequence[] items = new CharSequence[templates.size() + 1];
            int secondary = secondaryTextColor();
            for (int i = 0; i < templates.size(); i++) {
                IssueTemplates.Template t = templates.get(i);
                android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(t.name);
                if (!t.about.isEmpty()) {
                    int start = text.length();
                    text.append('\n').append(t.about);
                    text.setSpan(new android.text.style.RelativeSizeSpan(0.85f), start, text.length(), 0);
                    text.setSpan(new android.text.style.ForegroundColorSpan(secondary), start, text.length(), 0);
                }
                items[i] = text;
            }
            items[templates.size()] = getString(R.string.issue_template_blank);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.issue_template_choose)
                    .setItems(items, (d, which) -> {
                        if (which < templates.size()) applyTemplate(templates.get(which));
                    })
                    .setNegativeButton(R.string.issue_template_blank, null)
                    .show();
        }, false);
    }

    private void applyTemplate(@NonNull IssueTemplates.Template template) {
        if (!template.title.isEmpty()) {
            titleEdit.setText(template.title);
            titleEdit.setSelection(titleEdit.length());
        }
        commentEdit.setText(template.body);
        if (!mPresenter.isCanEditMeta()) return;
        if (!template.assignees.isEmpty()) {
            ArrayList<User> users = new ArrayList<>();
            for (String login : template.assignees) {
                User user = new User();
                user.setLogin(login);
                users.add(user);
            }
            mPresenter.getIssue().setAssignees(users);
            setAssigneesText();
        }
        if (template.labels.isEmpty()) return;
        // the labels' colors and ids come from the repository
        background(() -> GitHubApi.call(GitHubApi.service(IssueService.class)
                .getAllLabels(mPresenter.getRepoOwner(), mPresenter.getRepoName())), array -> {
            ArrayList<Label> labels = new ArrayList<>();
            for (String name : template.labels) {
                for (JsonElement element : array) {
                    JsonObject l = element.getAsJsonObject();
                    if (!name.equalsIgnoreCase(Json.str(l, "name"))) continue;
                    Label label = new Label();
                    label.setId(l.get("id").getAsLong());
                    label.setName(Json.str(l, "name"));
                    label.setColor(Json.str(l, "color"));
                    labels.add(label);
                }
            }
            mPresenter.getIssue().setLabels(labels);
            setLabelsText();
        }, false);
    }

    private int secondaryTextColor() {
        android.content.res.TypedArray array = obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        int color = array.getColor(0, 0xFF8B949E);
        array.recycle();
        return color;
    }

    private <T> void background(@NonNull java.util.concurrent.Callable<T> work,
                                @NonNull io.reactivex.rxjava3.functions.Consumer<T> onSuccess, boolean withProgress) {
        if (withProgress) showProgressDialog(getString(R.string.please_wait));
        disposables.add(io.reactivex.rxjava3.core.Single.fromCallable(work)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (withProgress) dismissProgressDialog();
                    onSuccess.accept(result);
                }, error -> {
                    if (withProgress) {
                        dismissProgressDialog();
                        showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                    }
                }));
    }

    @Override
    public void showNewIssue(@NonNull Issue issue) {
        showSuccessToast(mPresenter.isAddMode() ? getString(R.string.create_issue_success) :
                getString(R.string.edit_issue_success));
        Intent data = new Intent();
        data.putExtra("issue", issue);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onLoadLabelsComplete(ArrayList<Label> labels) {
        new ChooseLabelsDialog(getActivity(), labels, this).show();
    }

    public void onViewClicked(View view) {
        int viewId = view.getId();
        if (viewId == R.id.markdown_editor_bn) {
            MarkdownEditorActivity.show(getActivity(), R.string.edit,
                    MARKDOWN_EDITOR_REQUEST_CODE, commentEdit.getText().toString());
        } else if (viewId == R.id.edit_labels) {
            mPresenter.loadLabels();
        } else if (viewId == R.id.edit_assignees) {
            pickAssignees();
        } else if (viewId == R.id.edit_milestone) {
            pickMilestone();
        }
    }

    private boolean checkForCommit(){
        if(StringUtils.isBlank(titleEdit.getText().toString())){
            titleLayout.setError(getString(R.string.issue_title_null_tip));
            return false;
        }else {
            titleLayout.setErrorEnabled(false);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode != RESULT_OK) return;
        if(requestCode == MARKDOWN_EDITOR_REQUEST_CODE){
            String text = data.getStringExtra("text");
            commentEdit.setText(text);
            commentEdit.requestFocus();
            commentEdit.setSelection(text.length());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        titleEditStr = titleEdit.getText().toString();
        commentEditStr = commentEdit.getText().toString();
    }

    private void setLabelsText(){
        labelsText.setText(ViewUtils.getLabelsSpan(getActivity(), mPresenter.getLabels()));
    }

    @Override
    public void onChooseLabelsComplete(@NonNull ArrayList<Label> labels) {
        mPresenter.getIssue().setLabels(labels);
        setLabelsText();
    }

    @Override
    public void onShowManageLabels() {
        LabelManageActivity.show(getActivity(), mPresenter.getRepoOwner(), mPresenter.getRepoName());
        mPresenter.clearAllLabelsData();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        titleEdit = root.findViewById(R.id.title_edit);
        titleLayout = root.findViewById(R.id.title_layout);
        commentEdit = root.findViewById(R.id.comment_edit);
        commentLayout = root.findViewById(R.id.comment_layout);
        labelsText = root.findViewById(R.id.labels_text);
        editLabelsLay = root.findViewById(R.id.edit_labels);
        ViewUtils.setOnClick(root, R.id.markdown_editor_bn, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.edit_labels, v -> onViewClicked(v));
        assigneesText = root.findViewById(R.id.assignees_text);
        milestoneText = root.findViewById(R.id.milestone_text);
        metaSection = root.findViewById(R.id.meta_section);
        ViewUtils.setOnClick(root, R.id.edit_assignees, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.edit_milestone, v -> onViewClicked(v));
    }
}
