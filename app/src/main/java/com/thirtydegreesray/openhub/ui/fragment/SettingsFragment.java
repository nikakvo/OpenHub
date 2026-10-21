

package com.thirtydegreesray.openhub.ui.fragment;

import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import android.widget.Toast;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import android.content.Context;
import android.content.Intent;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.colorChooser.ColorChooserPreference;
import com.thirtydegreesray.openhub.util.AppUpdater;
import com.thirtydegreesray.openhub.util.AppIcons;
import com.thirtydegreesray.openhub.util.DrawerHeader;
import com.thirtydegreesray.openhub.util.DrawerIcons;
import com.thirtydegreesray.openhub.util.GitHubNotifier;
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
        findPreference(OPEN_LINKS).setOnPreferenceClickListener(this);
        findPreference(DrawerIcons.PREF).setOnPreferenceClickListener(this);
        updateMenuIconsSummary();
        findPreference(DrawerHeader.PREF).setOnPreferenceClickListener(this);
        updateMenuHeaderSummary();
        findPreference(AppIcons.PREF).setOnPreferenceClickListener(this);
        updateAppIconSummary();
        findPreference(GitHubNotifier.PREF_ENABLED).setOnPreferenceChangeListener(this);
        findPreference(GitHubNotifier.PREF_INTERVAL).setOnPreferenceClickListener(this);
        updateGitHubNotificationsSummary();
        findPreference(AppUpdater.PREF_CHECK_NOW).setSummary(
                getString(R.string.update_check_now_summary, AppUpdater.currentVersion()));
        updateWatchedReleasesSummary();
        findPreference(PrefUtils.START_PAGE).setSummary(nameList.get(getStartPageIndex()));
        ((ColorChooserPreference) findPreference(PrefUtils.ACCENT_COLOR))
                .setColorChooserCallback(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (GitHubNotifier.PREF_ENABLED.equals(preference.getKey()) && getContext() != null) {
            boolean enabled = Boolean.TRUE.equals(newValue);
            // the switch's value is saved after this returns; schedule once it is
            preference.getPreferenceManager().getSharedPreferences().edit()
                    .putBoolean(GitHubNotifier.PREF_ENABLED, enabled).apply();
            GitHubNotifier.onEnabledChanged(getContext(), enabled);
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getActivity() != null
                    && androidx.core.content.ContextCompat.checkSelfPermission(getContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(getActivity(),
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }
        return true;
    }

    private void updateGitHubNotificationsSummary() {
        Preference interval = findPreference(GitHubNotifier.PREF_INTERVAL);
        if (interval != null && getContext() != null) {
            interval.setSummary(getString(R.string.github_notifications_interval_summary,
                    GitHubNotifier.formatInterval(GitHubNotifier.getIntervalMinutes(getContext()))));
        }
    }

    private void showGitHubNotificationsIntervalChooser() {
        if (getContext() == null) return;
        int[] presets = GitHubNotifier.PRESET_MINUTES;
        int current = GitHubNotifier.getIntervalMinutes(getContext());
        String[] items = new String[presets.length];
        int checked = -1;
        for (int i = 0; i < presets.length; i++) {
            items[i] = GitHubNotifier.formatInterval(presets[i]);
            if (presets[i] == current) checked = i;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.github_notifications_interval)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    GitHubNotifier.setIntervalMinutes(getContext(), presets[which]);
                    updateGitHubNotificationsSummary();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
            case OPEN_LINKS:
                openLinkSettings();
                return true;
            case DrawerIcons.PREF:
                showMenuIconsChooser();
                return true;
            case DrawerHeader.PREF:
                showMenuHeaderChooser();
                return true;
            case AppIcons.PREF:
                showAppIconChooser();
                return true;
            case GitHubNotifier.PREF_INTERVAL:
                showGitHubNotificationsIntervalChooser();
                return true;
        }
        return false;
    }

    private static final String OPEN_LINKS = "openGitHubLinks";

    @Override
    public void onResume() {
        super.onResume();
        updateOpenLinksSummary();
    }

    /**
     * Since Android 12 an app only opens web links of sites it owns, unless the user
     * allows it under "Open by default". github.com can never be verified for OpenHub,
     * so the user has to add it there once; this shows whether that was done.
     */
    private void updateOpenLinksSummary() {
        Preference pref = findPreference(OPEN_LINKS);
        if (pref == null || getContext() == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pref.setSummary(R.string.open_links_summary_old);
            return;
        }
        boolean allowed = false;
        try {
            DomainVerificationManager manager = getContext().getSystemService(DomainVerificationManager.class);
            DomainVerificationUserState state = manager.getDomainVerificationUserState(getContext().getPackageName());
            if (state != null && state.isLinkHandlingAllowed()) {
                Integer github = state.getHostToStateMap().get("github.com");
                allowed = github != null && github != DomainVerificationUserState.DOMAIN_STATE_NONE;
            }
        } catch (Exception ignored) {
        }
        pref.setSummary(allowed ? R.string.open_links_summary_on : R.string.open_links_summary_off);
    }

    private void openLinkSettings() {
        if (getContext() == null) return;
        Uri app = Uri.parse("package:" + getContext().getPackageName());
        Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, app)
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app);
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toasty.info(getContext(), getString(R.string.open_links_hint), android.widget.Toast.LENGTH_LONG).show();
        }
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

    private void updateMenuIconsSummary() {
        Preference pref = findPreference(DrawerIcons.PREF);
        if (pref != null && getContext() != null) {
            pref.setSummary(DrawerIcons.label(DrawerIcons.getStyle(getContext())));
        }
    }

    private void showMenuIconsChooser() {
        if (getContext() == null) return;
        String current = DrawerIcons.getStyle(getContext());
        String[] items = new String[DrawerIcons.STYLES.length];
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(DrawerIcons.label(DrawerIcons.STYLES[i]));
            if (DrawerIcons.STYLES[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.menu_icons_title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putString(DrawerIcons.PREF, DrawerIcons.STYLES[which]).apply();
                    updateMenuIconsSummary();
                    recreateMain();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateMenuHeaderSummary() {
        Preference pref = findPreference(DrawerHeader.PREF);
        if (pref != null && getContext() != null) {
            pref.setSummary(DrawerHeader.label(DrawerHeader.getStyle(getContext())));
        }
    }

    private void showMenuHeaderChooser() {
        if (getContext() == null) return;
        String current = DrawerHeader.getStyle(getContext());
        String[] items = new String[DrawerHeader.STYLES.length];
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(DrawerHeader.label(DrawerHeader.STYLES[i]));
            if (DrawerHeader.STYLES[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.menu_header_title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putString(DrawerHeader.PREF, DrawerHeader.STYLES[which]).apply();
                    updateMenuHeaderSummary();
                    recreateMain();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateAppIconSummary() {
        Preference pref = findPreference(AppIcons.PREF);
        if (pref != null && getContext() != null) {
            pref.setSummary(AppIcons.label(AppIcons.current(getContext())));
        }
    }

    private void showAppIconChooser() {
        if (getContext() == null) return;
        String current = AppIcons.current(getContext());
        String[] items = new String[AppIcons.ICONS.length];
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(AppIcons.label(AppIcons.ICONS[i]));
            if (AppIcons.ICONS[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.app_icon_title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (AppIcons.ICONS[which].equals(current)) return;
                    AppIcons.set(getContext(), AppIcons.ICONS[which]);
                    updateAppIconSummary();
                    Toast.makeText(getContext(), R.string.app_icon_changed, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.cancel, null)
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
