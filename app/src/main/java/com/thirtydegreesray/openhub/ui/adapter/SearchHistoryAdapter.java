package com.thirtydegreesray.openhub.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.List;

/**
 * The earlier searches under the search box: each row has × to forget it, and the last
 * row "Clear search history" forgets all. Filtering while typing is ArrayAdapter's; the
 * last row is added after the matching searches. The × and the last row handle their
 * own taps, so the drop-down never puts their text into the search box.
 */
public class SearchHistoryAdapter extends ArrayAdapter<String> {

    public interface Host {
        void onRemoveSearch(@NonNull String search);

        void onClearSearches();
    }

    private static final int TYPE_SEARCH = 0;
    private static final int TYPE_CLEAR = 1;

    @NonNull private final Host host;
    private final LayoutInflater inflater;

    public SearchHistoryAdapter(@NonNull Context context, @NonNull List<String> searches, @NonNull Host host) {
        super(context, R.layout.layout_item_search_history, android.R.id.text1, searches);
        this.host = host;
        this.inflater = LayoutInflater.from(context);
    }

    /** Searches matching the typed text, plus the "Clear search history" row when there are any. */
    @Override
    public int getCount() {
        int searches = super.getCount();
        return searches == 0 ? 0 : searches + 1;
    }

    private boolean isClearRow(int position) {
        return position == super.getCount();
    }

    /** The last row has no text: picking it must not change the search box. */
    @Nullable
    @Override
    public String getItem(int position) {
        return isClearRow(position) ? "" : super.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return isClearRow(position) ? TYPE_CLEAR : TYPE_SEARCH;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (isClearRow(position)) {
            View row = convertView != null ? convertView
                    : inflater.inflate(R.layout.layout_item_search_history_clear, parent, false);
            ((TextView) row).setTextColor(ViewUtils.getAccentColor(getContext()));
            row.setOnClickListener(v -> host.onClearSearches());
            return row;
        }
        View row = super.getView(position, convertView, parent);
        final String search = super.getItem(position);
        View remove = row.findViewById(R.id.history_remove);
        remove.setOnClickListener(v -> {
            if (search != null) host.onRemoveSearch(search);
        });
        return row;
    }

    @NonNull
    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getView(position, convertView, parent);
    }
}
