package com.thirtydegreesray.openhub.ui.widget;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.LoginActivity;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * "Sign in again": shown when GitHub no longer accepts the saved sign-in of an account
 * (token expired, revoked, or unreadable on this phone). The account is never removed
 * here: it stays on the phone until the user signs in again (the new sign-in replaces
 * it) or logs out himself.
 */
public final class SignInPrompt {

    /** One prompt at a time, however many requests fail together. */
    private static boolean showing;

    private SignInPrompt() {
    }

    /**
     * @param login   the account that needs a new sign-in (suggested to GitHub), or null
     * @param leave   run after either button (the start screen closes itself); null keeps
     *                the current screen open (the prompt can then also be cancelled)
     */
    public static void show(@NonNull Activity activity, @Nullable String login,
                            boolean unreadable, @Nullable Runnable leave) {
        if (showing || activity.isFinishing() || activity.isDestroyed()) return;
        showing = true;
        String message;
        if (StringUtils.isBlank(login)) {
            message = activity.getString(R.string.sign_in_again_message_unknown);
        } else if (unreadable) {
            message = activity.getString(R.string.sign_in_again_message_unreadable, login);
        } else {
            message = activity.getString(R.string.sign_in_again_message, login);
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sign_in_again_title)
                .setMessage(message)
                .setCancelable(leave == null)
                .setPositiveButton(R.string.sign_in_again_button, (d, w) -> {
                    activity.startActivity(LoginActivity.createIntent(activity, login));
                    if (leave != null) leave.run();
                })
                .setNegativeButton(R.string.close, (d, w) -> {
                    if (leave != null) leave.run();
                })
                .setOnDismissListener(d -> showing = false)
                .show();
    }
}
