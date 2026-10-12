package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * Dialog with one text field, e.g. to rename a file. OK stays on the dialog and shows
 * an error while the text is blank.
 */
public final class TextInputDialog {

    public interface Callback {
        void onText(@NonNull String text);
    }

    private TextInputDialog() {
    }

    /**
     * @param fileName select the name without the extension, so typing replaces only it
     */
    public static void show(@NonNull Context context, @StringRes int title, @StringRes int hint,
                            @Nullable String initial, boolean fileName, @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = view.findViewById(R.id.input_layout);
        TextInputEditText edit = view.findViewById(R.id.input_edit);
        layout.setHint(context.getString(hint));
        if (initial != null) {
            edit.setText(initial);
            int end = initial.length();
            if (fileName) {
                int dot = initial.toLowerCase().endsWith(".tar.gz") ? end - 7 : initial.lastIndexOf('.');
                if (dot > 0) end = dot;
            }
            edit.setSelection(0, end);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        Runnable submit = () -> {
            String text = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (StringUtils.isBlank(text)) {
                layout.setError(context.getString(R.string.field_required));
                return;
            }
            dialog.dismiss();
            callback.onText(text);
        };
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> submit.run()));
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        edit.requestFocus();
    }
}
