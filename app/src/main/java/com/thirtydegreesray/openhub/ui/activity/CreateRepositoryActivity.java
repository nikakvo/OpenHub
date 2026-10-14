package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.AccountChanges;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoAdminService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.GitignoreTemplate;
import com.thirtydegreesray.openhub.mvp.model.LicenseTemplate;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.GitignorePickerDialog;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * "New repository", like github.com/new: owner (your account or one of your
 * organizations), name with a live check whether it is free, description, public or
 * private, and an optional first commit with a README, a .gitignore and a license.
 */
public class CreateRepositoryActivity extends BaseActivity<IBaseContract.Presenter> {

    public static void show(@NonNull Activity activity) {
        activity.startActivity(new Intent(activity, CreateRepositoryActivity.class));
    }

    /** GitHub keeps letters, digits, '-', '_' and '.'; everything else becomes '-'. */
    @NonNull
    static String normalizeName(@NonNull String name) {
        return name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static final long NAME_CHECK_DELAY_MS = 600;

    /** null: the user's own account */
    @AutoAccess String org;
    @AutoAccess String orgAvatar;
    @AutoAccess ArrayList<String> gitignoreTemplates;
    @AutoAccess String gitignoreCustom;
    @AutoAccess String licenseKey;
    @AutoAccess String licenseName;

    private ImageView ownerAvatar;
    private TextView ownerText;
    private View ownerArrow;
    private TextInputLayout nameLayout;
    private TextInputEditText nameEdit;
    private TextView nameStatus;
    private TextInputEditText descriptionEdit;
    private RadioButton privateRadio;
    private CheckBox readmeCheck;
    private TextView gitignoreText;
    private TextView licenseText;
    private TextView summaryText;
    private Button createBn;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Disposable nameCheck;
    /** normalized name that is known to be taken, or null */
    private String takenName;
    private ArrayList<User> orgs;
    private ArrayList<String> allGitignoreTemplates;
    private ArrayList<LicenseTemplate> licenses;
    private boolean creating;
    private volatile String gitignoreError;

    private final Runnable checkNameRunnable = this::checkNameAvailable;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_create_repository;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.repo_create_title));
        showOwner();
        if (gitignoreTemplates == null) gitignoreTemplates = new ArrayList<>();
        showGitignore();
        licenseText.setText(licenseName == null ? getString(R.string.repo_create_none) : licenseName);
        loadOrgs();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onNameChanged();
            }
        });
        privateRadio.setOnCheckedChangeListener((v, checked) -> updateSummary());
        onNameChanged();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(checkNameRunnable);
        disposables.clear();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------
    // Owner
    // ---------------------------------------------------------------------------------

    @NonNull
    private String ownerLogin() {
        return org != null ? org : AppData.INSTANCE.getLoggedUser().getLogin();
    }

    private void showOwner() {
        ownerText.setText(ownerLogin());
        String avatar = org != null ? orgAvatar : AppData.INSTANCE.getLoggedUser().getAvatarUrl();
        GlideApp.with(this).load(avatar).into(ownerAvatar);
    }

    private void loadOrgs() {
        background(() -> GitHubApi.call(GitHubApi.service(RepoAdminService.class).getMyOrgs(true)),
                (ArrayList<User> list) -> {
                    orgs = list;
                    ownerArrow.setVisibility(orgs.isEmpty() ? View.GONE : View.VISIBLE);
                }, null, false);
    }

    private void chooseOwner() {
        if (orgs == null || orgs.isEmpty()) return;
        String[] items = new String[orgs.size() + 1];
        items[0] = AppData.INSTANCE.getLoggedUser().getLogin();
        int checked = 0;
        for (int i = 0; i < orgs.size(); i++) {
            items[i + 1] = orgs.get(i).getLogin();
            if (orgs.get(i).getLogin().equals(org)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.repo_create_owner)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        org = null;
                        orgAvatar = null;
                    } else {
                        org = orgs.get(which - 1).getLogin();
                        orgAvatar = orgs.get(which - 1).getAvatarUrl();
                    }
                    dialog.dismiss();
                    showOwner();
                    onNameChanged();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Name
    // ---------------------------------------------------------------------------------

    @NonNull
    private String typedName() {
        return nameEdit.getText() == null ? "" : nameEdit.getText().toString().trim();
    }

    private void onNameChanged() {
        handler.removeCallbacks(checkNameRunnable);
        if (nameCheck != null) nameCheck.dispose();
        takenName = null;
        nameLayout.setErrorEnabled(false);
        String typed = typedName();
        String name = normalizeName(typed);
        if (typed.isEmpty()) {
            nameStatus.setText(R.string.repo_create_name_hint);
        } else if (name.equals(".") || name.equals("..")) {
            nameLayout.setError(getString(R.string.repo_create_name_reserved));
            nameStatus.setText("");
        } else {
            nameStatus.setText(name.equals(typed)
                    ? getString(R.string.repo_create_checking)
                    : getString(R.string.repo_create_will_be_named, name));
            handler.postDelayed(checkNameRunnable, NAME_CHECK_DELAY_MS);
        }
        updateSummary();
    }

    private void checkNameAvailable() {
        final String owner = ownerLogin();
        final String name = normalizeName(typedName());
        if (name.isEmpty()) return;
        nameCheck = Single.fromCallable(() -> {
            try {
                GitHubApi.call(GitHubApi.service(RepoAdminService.class).getRepo(owner, name));
                return true;
            } catch (GitHubApi.ApiException e) {
                if (e.code == 404) return false;
                throw e;
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(exists -> {
                    if (!name.equals(normalizeName(typedName())) || !owner.equals(ownerLogin())) return;
                    if (exists) {
                        takenName = name;
                        nameLayout.setError(getString(R.string.repo_create_name_taken, owner, name));
                        nameStatus.setText("");
                    } else {
                        String typed = typedName();
                        nameStatus.setText(name.equals(typed)
                                ? getString(R.string.repo_create_name_free, name)
                                : getString(R.string.repo_create_will_be_named, name));
                    }
                    updateSummary();
                }, error -> nameStatus.setText(""));
        disposables.add(nameCheck);
    }

    private boolean isNameValid() {
        String name = normalizeName(typedName());
        return !name.isEmpty() && !name.equals(".") && !name.equals("..") && !name.equals(takenName);
    }

    private void updateSummary() {
        boolean isPrivate = privateRadio.isChecked();
        String where = org == null ? getString(R.string.repo_create_in_account)
                : getString(R.string.repo_create_in_org, org);
        summaryText.setText(getString(isPrivate ? R.string.repo_create_summary_private
                : R.string.repo_create_summary_public, where));
        createBn.setEnabled(!creating && isNameValid());
    }

    // ---------------------------------------------------------------------------------
    // .gitignore and license
    // ---------------------------------------------------------------------------------

    private void chooseGitignore() {
        if (allGitignoreTemplates == null) {
            showLoading();
            background(() -> GitHubApi.call(GitHubApi.service(RepoAdminService.class)
                    .getGitignoreTemplates(false)), (ArrayList<String> list) -> {
                hideLoading();
                allGitignoreTemplates = list;
                chooseGitignore();
            }, error -> hideLoading(), true);
            return;
        }
        GitignorePickerDialog.show(this, allGitignoreTemplates, gitignoreTemplates, gitignoreCustom,
                (templates, custom) -> {
                    gitignoreTemplates = templates;
                    gitignoreCustom = custom.isEmpty() ? null : custom;
                    showGitignore();
                });
    }

    private void showGitignore() {
        int customCount = customGitignoreLines().size();
        String text;
        if (gitignoreTemplates.isEmpty() && customCount == 0) {
            text = getString(R.string.repo_create_none);
        } else {
            text = String.join(", ", gitignoreTemplates);
            if (customCount > 0) {
                String custom = getResources().getQuantityString(R.plurals.gitignore_custom_count,
                        customCount, customCount);
                text = text.isEmpty() ? custom : text + " + " + custom;
            }
        }
        gitignoreText.setText(text);
    }

    @NonNull
    private List<String> customGitignoreLines() {
        List<String> lines = new ArrayList<>();
        if (gitignoreCustom == null) return lines;
        for (String line : gitignoreCustom.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) lines.add(line.trim());
        }
        return lines;
    }

    /**
     * One template alone is applied by GitHub while it creates the repository; several
     * templates or own lines are combined into one .gitignore and committed after it.
     */
    private boolean gitignoreAsFile() {
        return gitignoreTemplates.size() > 1 || !customGitignoreLines().isEmpty();
    }

    @NonNull
    private static String buildGitignore(@NonNull RepoAdminService service, @NonNull List<String> templates,
                                         @NonNull List<String> custom) throws java.io.IOException {
        StringBuilder text = new StringBuilder();
        for (String name : templates) {
            GitignoreTemplate template = GitHubApi.call(service.getGitignoreTemplate(false, name));
            if (template == null || template.getSource() == null) continue;
            if (text.length() > 0) text.append("\n");
            text.append("# ===== ").append(name).append(" =====\n");
            text.append(template.getSource().trim()).append("\n");
        }
        if (!custom.isEmpty()) {
            if (text.length() > 0) text.append("\n");
            text.append("# ===== Custom =====\n");
            for (String line : custom) text.append(line).append("\n");
        }
        return text.toString();
    }

    private void chooseLicense() {
        if (licenses == null) {
            showLoading();
            background(() -> GitHubApi.call(GitHubApi.service(RepoAdminService.class)
                    .getLicenses(false)), (ArrayList<LicenseTemplate> list) -> {
                hideLoading();
                licenses = list;
                chooseLicense();
            }, error -> hideLoading(), true);
            return;
        }
        String[] items = new String[licenses.size() + 1];
        items[0] = getString(R.string.repo_create_none);
        int checked = 0;
        for (int i = 0; i < licenses.size(); i++) {
            items[i + 1] = licenses.get(i).getName();
            if (licenses.get(i).getKey().equals(licenseKey)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.repo_create_license)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        licenseKey = null;
                        licenseName = null;
                    } else {
                        licenseKey = licenses.get(which - 1).getKey();
                        licenseName = licenses.get(which - 1).getName();
                    }
                    licenseText.setText(items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------------------

    private void create() {
        if (creating || !isNameValid()) return;
        final String name = normalizeName(typedName());
        final Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        String description = descriptionEdit.getText() == null ? ""
                : descriptionEdit.getText().toString().trim();
        if (!description.isEmpty()) body.put("description", description);
        body.put("private", privateRadio.isChecked());
        body.put("auto_init", readmeCheck.isChecked());
        final boolean gitignoreFile = gitignoreAsFile();
        if (!gitignoreFile && gitignoreTemplates.size() == 1) {
            body.put("gitignore_template", gitignoreTemplates.get(0));
        }
        if (licenseKey != null) body.put("license_template", licenseKey);
        final ArrayList<String> templates = new ArrayList<>(gitignoreTemplates);
        final List<String> customLines = customGitignoreLines();

        final String owner = org;
        gitignoreError = null;
        creating = true;
        updateSummary();
        showProgressDialog(getString(R.string.repo_create_creating));
        background(() -> {
            RepoAdminService service = GitHubApi.service(RepoAdminService.class);
            Repository repository = GitHubApi.call(owner == null ? service.createUserRepo(body)
                    : service.createOrgRepo(owner, body));
            if (repository == null) throw new java.io.IOException("Empty answer");
            if (gitignoreFile) {
                try {
                    String text = buildGitignore(service, templates, customLines);
                    Map<String, Object> file = new HashMap<>();
                    file.put("message", "Add .gitignore");
                    file.put("content", Base64.encodeToString(
                            text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                    // a repository that was created a moment ago can answer 404/409 briefly
                    for (int attempt = 1; ; attempt++) {
                        try {
                            GitHubApi.call(service.putFile(repository.getOwner().getLogin(),
                                    repository.getName(), ".gitignore", file));
                            break;
                        } catch (GitHubApi.ApiException e) {
                            if (attempt >= 4 || (e.code != 404 && e.code != 409)) throw e;
                            Thread.sleep(1500L * attempt);
                        }
                    }
                } catch (Exception e) {
                    // the repository exists; say what is missing instead of failing it
                    gitignoreError = e.getMessage();
                }
            }
            return repository;
        }, (Repository repository) -> {
            dismissProgressDialog();
            if (gitignoreError != null) {
                showWarningToast(getString(R.string.gitignore_commit_failed, gitignoreError));
            }
            AccountChanges.markRepos();
            showSuccessToast(getString(R.string.repo_create_done, repository.getFullName()));
            RepositoryActivity.show(this, repository);
            finish();
        }, error -> {
            dismissProgressDialog();
            creating = false;
            updateSummary();
        }, true);
    }

    // ---------------------------------------------------------------------------------

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError, boolean showError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    if (showError) {
                        showErrorToast(StringUtils.isBlank(error.getMessage())
                                ? error.toString() : error.getMessage());
                    }
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    public void showLoading() {
        loader.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        loader.setVisibility(View.GONE);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        ownerAvatar = root.findViewById(R.id.owner_avatar);
        ownerText = root.findViewById(R.id.owner_text);
        ownerArrow = root.findViewById(R.id.owner_arrow);
        nameLayout = root.findViewById(R.id.name_layout);
        nameEdit = root.findViewById(R.id.name_edit);
        nameStatus = root.findViewById(R.id.name_status);
        descriptionEdit = root.findViewById(R.id.description_edit);
        privateRadio = root.findViewById(R.id.visibility_private);
        readmeCheck = root.findViewById(R.id.readme_check);
        gitignoreText = root.findViewById(R.id.gitignore_text);
        licenseText = root.findViewById(R.id.license_text);
        summaryText = root.findViewById(R.id.summary_text);
        createBn = root.findViewById(R.id.create_bn);
        loader = root.findViewById(R.id.loader);
        ownerArrow.setVisibility(View.GONE);
        ViewUtils.setOnClick(root, R.id.owner_row, v -> chooseOwner());
        ViewUtils.setOnClick(root, R.id.gitignore_row, v -> chooseGitignore());
        ViewUtils.setOnClick(root, R.id.license_row, v -> chooseLicense());
        ViewUtils.setOnClick(root, R.id.create_bn, v -> create());
    }
}
