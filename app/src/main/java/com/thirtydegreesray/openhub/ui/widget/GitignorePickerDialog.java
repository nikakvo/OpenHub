package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.thirtydegreesray.openhub.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Picks .gitignore templates for a new repository: a searchable list where several
 * templates can be ticked (chosen ones stay on top), plus free lines of your own.
 */
public final class GitignorePickerDialog {

    public interface Callback {
        void onPicked(@NonNull ArrayList<String> templates, @NonNull String customLines);
    }

    private GitignorePickerDialog() {
    }

    public static void show(@NonNull Context context, @NonNull List<String> templates,
                            @Nullable List<String> selectedTemplates, @Nullable String customLines,
                            @NonNull Callback callback) {
        View view = LayoutInflater.from(new AlertDialog.Builder(context).getContext()).inflate(R.layout.dialog_gitignore_picker, null);
        TextInputEditText searchEdit = view.findViewById(R.id.search_edit);
        TextView selectedText = view.findViewById(R.id.selected_text);
        ListView listView = view.findViewById(R.id.template_list);
        TextView emptyText = view.findViewById(R.id.empty_text);
        TextInputEditText customEdit = view.findViewById(R.id.custom_edit);
        if (customLines != null) customEdit.setText(customLines);

        // keeps the order in which templates were ticked
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (selectedTemplates != null) selected.addAll(selectedTemplates);
        ArrayList<String> shown = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_multiple_choice, shown);
        listView.setAdapter(adapter);

        Runnable refresh = () -> {
            String query = searchEdit.getText() == null ? ""
                    : searchEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            if (query.isEmpty()) {
                // chosen ones first, so they are easy to find and untick
                for (String name : templates) if (selected.contains(name)) shown.add(name);
                for (String name : templates) if (!selected.contains(name)) shown.add(name);
            } else {
                for (String name : templates) {
                    if (name.toLowerCase(Locale.ROOT).contains(query)) shown.add(name);
                }
            }
            adapter.notifyDataSetChanged();
            for (int i = 0; i < shown.size(); i++) {
                listView.setItemChecked(i, selected.contains(shown.get(i)));
            }
            emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            selectedText.setText(selected.isEmpty()
                    ? context.getString(R.string.gitignore_none_selected)
                    : context.getString(R.string.gitignore_selected, String.join(", ", selected)));
        };

        listView.setOnItemClickListener((parent, v, position, id) -> {
            String name = shown.get(position);
            if (listView.isItemChecked(position)) selected.add(name);
            else selected.remove(name);
            selectedText.setText(selected.isEmpty()
                    ? context.getString(R.string.gitignore_none_selected)
                    : context.getString(R.string.gitignore_selected, String.join(", ", selected)));
        });
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });
        refresh.run();

        new AlertDialog.Builder(context)
                .setTitle(R.string.repo_create_gitignore)
                .setView(view)
                .setPositiveButton(R.string.ok, (d, w) -> callback.onPicked(new ArrayList<>(selected),
                        customEdit.getText() == null ? "" : customEdit.getText().toString().trim()))
                .setNeutralButton(R.string.repo_create_none, (d, w) ->
                        callback.onPicked(new ArrayList<>(), ""))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
