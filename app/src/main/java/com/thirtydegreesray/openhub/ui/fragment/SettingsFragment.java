

package com.thirtydegreesray.openhub.ui.fragment;

import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.colorChooser.ColorChooserPreference;
import com.thirtydegreesray.openhub.util.AppUpdater;
import com.thirtydegreesray.openhub.util.PrefUtils;

import java.util.Arrays;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Created on 2017/8/3.
 *
 * @author ThirtyDegreesRay
 */

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener,
        ColorChooserPreference.ColorChooserCallback {

    @Override
    public void onColorChanged(@ColorInt int oriColor, @ColorInt int selectedColor) {
        recreateMain();
    }

    public interface SettingsCallBack {
        void onLogout();

        void onRecreate();
    }

    private SettingsCallBack callBack;

    private List<String> idList ;
    private List<String> nameList ;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        callBack = (SettingsCallBack) context;
        idList = Arrays.asList(getResources().getStringArray(R.array.start_pages_id));
        nameList = Arrays.asList(getResources().getStringArray(R.array.start_pages_name));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callBack = null;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        findPreference(PrefUtils.THEME).setOnPreferenceClickListener(this);
//        findPreference(PrefUtils.LOGOUT).setOnPreferenceClickListener(this);
        findPreference(PrefUtils.START_PAGE).setOnPreferenceClickListener(this);
        findPreference(WATCHED_RELEASES).setOnPreferenceClickListener(this);
        findPreference(RELEASE_CHECK_INTERVAL).setOnPreferenceClickListener(this);
        findPreference(AppUpdater.PREF_CHECK_NOW).setOnPreferenceClickListener(this);
        findPreference(AppUpdater.PREF_CHECK_NOW).setSummary(
                getString(R.string.update_check_now_summary, AppUpdater.currentVersion()));
        updateWatchedReleasesSummary();
        findPreference(PrefUtils.START_PAGE).setSummary(nameList.get(getStartPageIndex()));
        ((ColorChooserPreference) findPreference(PrefUtils.ACCENT_COLOR))
                .setColorChooserCallback(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case PrefUtils.THEME:
                showThemeChooser();
                return true;
            case PrefUtils.LOGOUT:
                logout();
                return true;
            case PrefUtils.START_PAGE:
                showChooseStartPageDialog();
                return true;
            case WATCHED_RELEASES:
                showWatchedReleases();
                return true;
            case RELEASE_CHECK_INTERVAL:
                showReleaseIntervalChooser();
                return true;
            case AppUpdater.PREF_CHECK_NOW:
                if (getActivity() != null) AppUpdater.check(getActivity(), true);
                return true;
        }
        return false;
    }

    private static final String WATCHED_RELEASES = "watchedReleases";
    private static final String RELEASE_CHECK_INTERVAL = "releaseCheckInterval";

    private void updateWatchedReleasesSummary() {
        int count = ReleaseWatcher.getWatched(getContext()).size();
        String interval = ReleaseWatcher.formatInterval(ReleaseWatcher.getIntervalMinutes(getContext()));
        findPreference(WATCHED_RELEASES).setSummary(count == 0
                ? getString(R.string.watched_releases_summary_none)
                : getString(R.string.watched_releases_summary, count, interval));
        findPreference(RELEASE_CHECK_INTERVAL).setSummary(interval);
    }

    /** Presets 1 h ... 1 day plus Custom (minutes, 15 .. 10080). */
    private void showReleaseIntervalChooser() {
        final int[] presets = ReleaseWatcher.PRESET_MINUTES;
        int current = ReleaseWatcher.getIntervalMinutes(getContext());
        String[] items = new String[presets.length + 1];
        int checked = presets.length;
        for (int i = 0; i < presets.length; i++) {
            items[i] = ReleaseWatcher.formatInterval(presets[i]);
            if (presets[i] == current) checked = i;
        }
        items[presets.length] = checked == presets.length
                ? getString(R.string.release_check_custom) + " (" + ReleaseWatcher.formatInterval(current) + ")"
                : getString(R.string.release_check_custom);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.release_check_interval)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which < presets.length) {
                        applyReleaseInterval(presets[which]);
                    } else {
                        showCustomReleaseInterval();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCustomReleaseInterval() {
        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.release_check_custom_hint);
        input.setText(String.valueOf(ReleaseWatcher.getIntervalMinutes(getContext())));
        input.setSelectAllOnFocus(true);
        FrameLayout container = new FrameLayout(getContext());
        int padding = getResources().getDimensionPixelSize(R.dimen.spacing_x_large);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.release_check_interval)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (dialog, which) -> {
                    try {
                        applyReleaseInterval(Integer.parseInt(input.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                    }
                })
                .show();
    }

    private void applyReleaseInterval(int minutes) {
        ReleaseWatcher.setIntervalMinutes(getContext(), minutes);
        updateWatchedReleasesSummary();
        Toasty.success(getContext(), getString(R.string.release_check_interval_set,
                ReleaseWatcher.formatInterval(ReleaseWatcher.getIntervalMinutes(getContext())))).show();
    }

    /** Watched repositories with checkboxes: unchecking one stops its notifications. */
    private void showWatchedReleases() {
        final List<String> repos = ReleaseWatcher.getWatched(getContext());
        if (repos.isEmpty()) {
            Toasty.info(getContext(), getString(R.string.watched_releases_summary_none)).show();
            return;
        }
        final String[] items = repos.toArray(new String[0]);
        final boolean[] checked = new boolean[items.length];
        java.util.Arrays.fill(checked, true);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.watched_releases)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton(R.string.check_now, (dialog, which) -> {
                    applyWatched(items, checked);
                    ReleaseWatcher.checkNow(getContext());
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (dialog, which) -> applyWatched(items, checked))
                .show();
    }

    private void applyWatched(String[] items, boolean[] checked) {
        for (int i = 0; i < items.length; i++) {
            if (!checked[i]) ReleaseWatcher.setWatched(getContext(), items[i], false);
        }
        updateWatchedReleasesSummary();
    }

    private void showThemeChooser() {
        final List<String> valueList
                = Arrays.asList(getResources().getStringArray(R.array.theme_array));
        String theme = PrefUtils.getTheme();
        int selectIndex = valueList.indexOf(theme);
        new AlertDialog.Builder(getContext())
                .setCancelable(true)
                .setTitle(R.string.choose_theme)
                .setSingleChoiceItems(R.array.theme_array, selectIndex, (dialog1, which) -> {
                    dialog1.dismiss();
                    PrefUtils.set(PrefUtils.THEME, valueList.get(which));
                    recreateMain();
                })
                .show();
    }

    private void recreateMain() {
        callBack.onRecreate();
    }

    private void logout() {
        new AlertDialog.Builder(getContext())
                .setCancelable(true)
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(R.string.logout_warning)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    callBack.onLogout();
                    dialog.dismiss();
                })
                .show();
    }

    private void showChooseStartPageDialog(){
        new AlertDialog.Builder(getContext())
                .setCancelable(true)
                .setTitle(R.string.start_page)
                .setSingleChoiceItems(R.array.start_pages_name, getStartPageIndex(), (dialog, which) -> {
                    dialog.dismiss();
                    PrefUtils.set(PrefUtils.START_PAGE, idList.get(which));
                    findPreference(PrefUtils.START_PAGE).setSummary(nameList.get(which));
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {

                })
                .show();
    }

    private int getStartPageIndex(){
        String startPage = PrefUtils.getStartPage();
        return idList.indexOf(startPage);
    }

}
