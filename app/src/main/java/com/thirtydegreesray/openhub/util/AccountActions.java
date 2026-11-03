package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AuthUserDao;
import com.thirtydegreesray.openhub.http.core.AppRetrofit;
import com.thirtydegreesray.openhub.ui.activity.LoginActivity;
import com.thirtydegreesray.openhub.ui.activity.SplashActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Switching accounts, adding one and logging out, for the side menu of every screen (the
 * start screen's menu and the menu panel of the other screens do the same).
 */
public final class AccountActions {

    private AccountActions() {
    }

    @NonNull
    private static AuthUserDao dao() {
        return AppApplication.get().getAppComponent().getDatabase().authUserDao();
    }

    /** The saved accounts except the one in use. */
    @NonNull
    public static List<AuthUser> others() {
        List<AuthUser> result = new ArrayList<>();
        String current = AppData.INSTANCE.getLoggedUser() == null ? null
                : AppData.INSTANCE.getLoggedUser().getLogin();
        try {
            for (AuthUser user : dao().loadAll()) {
                if (current == null || !current.equals(user.getLoginId())) result.add(user);
            }
        } catch (Exception ignored) {
            // no accounts to offer
        }
        return result;
    }

    public static void switchTo(@NonNull Activity activity, @NonNull String loginId) {
        dao().select(loginId);
        AppRetrofit.INSTANCE.clearClients();
        AppData.INSTANCE.setAuthUser(null);
        AppData.INSTANCE.setLoggedUser(null);
        restart(activity);
    }

    public static void addAccount(@NonNull Activity activity) {
        activity.startActivity(new Intent(activity, LoginActivity.class));
    }

    /** Asks first, then removes the account in use from the phone (and revokes its sign-in). */
    public static void confirmLogout(@NonNull Activity activity) {
        new AlertDialog.Builder(activity)
                .setCancelable(true)
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(R.string.logout_warning)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.logout, (dialog, which) -> logout(activity))
                .show();
    }

    public static void logout(@NonNull Activity activity) {
        AuthUser user = AppData.INSTANCE.getAuthUser();
        String token = AppData.INSTANCE.getAccessToken();
        String login = user == null ? null : user.getLoginId();
        AuthUserDao dao = dao();
        if (user != null) dao.delete(user);
        // also by login name, so no row can stay behind whatever form its token has
        if (login != null) dao.deleteByLogin(login);
        SignOut.cleanUp(token);
        AppData.INSTANCE.setAuthUser(null);
        AppData.INSTANCE.setLoggedUser(null);
        restart(activity);
    }

    private static void restart(@Nullable Activity activity) {
        if (activity == null) return;
        activity.finishAffinity();
        activity.startActivity(new Intent(activity, SplashActivity.class));
    }
}
