package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * "Commit changes", like on github.com: message, optional extended description, and
 * either the current branch or a new branch with an optional pull request.
 */
public final class CommitDialog {

    public interface Callback {
        void onCommit(@NonNull RepoCommitter.Options options);
    }

    private CommitDialog() {
    }

    public static void show(@NonNull Context context, @NonNull String branch,
                            @NonNull String defaultMessage, @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_commit, null);
        TextInputLayout messageLayout = view.findViewById(R.id.message_layout);
        TextInputEditText messageEdit = view.findViewById(R.id.message_edit);
        TextInputEditText descriptionEdit = view.findViewById(R.id.description_edit);
        RadioButton current = view.findViewById(R.id.target_current);
        RadioButton newBranch = view.findViewById(R.id.target_new);
        View newBranchSection = view.findViewById(R.id.new_branch_section);
        TextInputLayout branchLayout = view.findViewById(R.id.branch_layout);
        TextInputEditText branchEdit = view.findViewById(R.id.branch_edit);
        CheckBox pullCheck = view.findViewById(R.id.pull_check);

        // prefilled like on github.com; typing replaces it
        messageEdit.setText(defaultMessage);
        messageEdit.selectAll();
        current.setText(Html.fromHtml(context.getString(R.string.commit_to_current_branch,
                Html.escapeHtml(branch)), Html.FROM_HTML_MODE_LEGACY));
        String login = AppData.INSTANCE.getLoggedUser() == null ? "patch"
                : AppData.INSTANCE.getLoggedUser().getLogin();
        branchEdit.setText(login + "-patch-" + (System.currentTimeMillis() / 1000 % 100000));
        newBranch.setOnCheckedChangeListener((v, checked) ->
                newBranchSection.setVisibility(checked ? View.VISIBLE : View.GONE));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.commit_title)
                .setView(view)
                .setPositiveButton(R.string.commit_button, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String message = messageEdit.getText() == null ? "" : messageEdit.getText().toString().trim();
            if (message.isEmpty()) message = defaultMessage;
            String description = descriptionEdit.getText() == null ? null
                    : descriptionEdit.getText().toString().trim();
            String branchName = null;
            if (newBranch.isChecked()) {
                branchName = branchEdit.getText() == null ? "" : branchEdit.getText().toString().trim();
                if (!isValidBranchName(branchName)) {
                    branchLayout.setError(context.getString(R.string.commit_branch_invalid));
                    return;
                }
                if (branchName.equals(branch)) {
                    branchLayout.setError(context.getString(R.string.commit_branch_same));
                    return;
                }
            }
            messageLayout.setErrorEnabled(false);
            dialog.dismiss();
            callback.onCommit(new RepoCommitter.Options(message,
                    StringUtils.isBlank(description) ? null : description, branchName,
                    branchName != null && pullCheck.isChecked()));
        }));
        dialog.show();
    }

    /** The basic rules of git check-ref-format. */
    static boolean isValidBranchName(@NonNull String name) {
        if (name.isEmpty() || name.startsWith("/") || name.endsWith("/") || name.endsWith(".")
                || name.endsWith(".lock") || name.contains("..") || name.contains("//")
                || name.contains("@{") || name.startsWith("-")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (c <= ' ' || c == '~' || c == '^' || c == ':' || c == '?' || c == '*' || c == '['
                    || c == '\\' || c == 127) {
                return false;
            }
        }
        return true;
    }
}
