package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.R;

/**
 * A dialog to write Markdown with Write / Preview, optionally with a one-line title
 * and a choice (e.g. Comment / Approve / Request changes, or the merge method).
 * Used for pull request comments, reviews, editing and merging.
 */
public final class ComposeDialog {

    public interface Callback {
        void onDone(int choice, @NonNull String title, @NonNull String body);
    }

    private ComposeDialog() {
    }

    public static final class Builder {
        private final Context context;
        private CharSequence dialogTitle;
        @Nullable private String repoFullName;
        @Nullable private CharSequence[] choices;
        private int choice;
        @Nullable private CharSequence titleHint;
        @Nullable private String titleText;
        private CharSequence bodyHint = "";
        @Nullable private String bodyText;
        private CharSequence positive;
        private boolean bodyRequired;
        /** choices whose body may stay empty although the body is required otherwise */
        @Nullable private boolean[] bodyOptional;
        @Nullable private Callback callback;

        public Builder(@NonNull Context context) {
            this.context = context;
            this.positive = context.getString(R.string.ok);
        }

        public Builder title(@NonNull CharSequence title) { this.dialogTitle = title; return this; }
        public Builder repo(@Nullable String fullName) { this.repoFullName = fullName; return this; }
        public Builder choices(@NonNull CharSequence[] choices, int selected) {
            this.choices = choices;
            this.choice = selected;
            return this;
        }
        public Builder titleField(@NonNull CharSequence hint, @Nullable String text) {
            this.titleHint = hint;
            this.titleText = text;
            return this;
        }
        public Builder body(@NonNull CharSequence hint, @Nullable String text) {
            this.bodyHint = hint;
            this.bodyText = text;
            return this;
        }
        public Builder bodyRequired(boolean required, @Nullable boolean[] optionalForChoice) {
            this.bodyRequired = required;
            this.bodyOptional = optionalForChoice;
            return this;
        }
        public Builder positive(@NonNull CharSequence label) { this.positive = label; return this; }
        public Builder callback(@NonNull Callback callback) { this.callback = callback; return this; }

        public void show() {
            View view = LayoutInflater.from(context).inflate(R.layout.dialog_pr_compose, null);
            RadioGroup group = view.findViewById(R.id.choice_group);
            TextInputLayout titleLayout = view.findViewById(R.id.title_layout);
            TextInputEditText titleEdit = view.findViewById(R.id.title_edit);
            MarkdownWritePreview editor = new MarkdownWritePreview(view, repoFullName);
            editor.setHint(bodyHint);
            if (bodyText != null) editor.getEditText().setText(bodyText);

            if (choices != null) {
                group.setVisibility(View.VISIBLE);
                for (int i = 0; i < choices.length; i++) {
                    RadioButton button = new RadioButton(context);
                    button.setId(View.generateViewId());
                    button.setText(choices[i]);
                    button.setTag(i);
                    group.addView(button);
                    if (i == choice) button.setChecked(true);
                }
            }
            if (titleHint != null) {
                titleLayout.setVisibility(View.VISIBLE);
                titleLayout.setHint(titleHint);
                if (titleText != null) titleEdit.setText(titleText);
            }

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(dialogTitle)
                    .setView(view)
                    .setPositiveButton(positive, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int selected = 0;
                if (choices != null) {
                    View checked = group.findViewById(group.getCheckedRadioButtonId());
                    selected = checked == null ? 0 : (int) checked.getTag();
                }
                String title = titleEdit.getText() == null ? "" : titleEdit.getText().toString().trim();
                String body = editor.getText().trim();
                if (titleHint != null && title.isEmpty()) {
                    titleLayout.setError(context.getString(R.string.field_required));
                    return;
                }
                boolean optional = bodyOptional != null && selected < bodyOptional.length && bodyOptional[selected];
                if (bodyRequired && !optional && body.isEmpty()) {
                    editor.showWrite();
                    editor.getEditText().setError(context.getString(R.string.field_required));
                    return;
                }
                dialog.dismiss();
                if (callback != null) callback.onDone(selected, title, body);
            }));
            dialog.setOnDismissListener(d -> editor.dispose());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            dialog.show();
        }
    }
}
