package com.thirtydegreesray.openhub.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.SplashActivity;

import java.util.HashSet;
import java.util.Set;

/**
 * Accounts whose expiring browser sign-in GitHub no longer refreshes ("bad_refresh_token":
 * the refresh token ran out after about 6 months, or the user revoked OpenHub on
 * github.com). The mark is kept on the phone, so background checks stop asking GitHub
 * with a dead sign-in also after a restart. The account itself is never removed.
 *
 * When the mark is set while OpenHub is not on screen, ONE notification says so
 * (inside the app the "Sign in again" dialog does that). A new sign-in of the account
 * clears the mark and the notification.
 */
public final class SignInNotice {

    private static final String PREFS = "sign_in_state";
    private static final String KEY_REFUSED = "refused_logins";
    private static final String CHANNEL_ID = "account";
    private static final String TAG = "sign_in_again";

    private SignInNotice() {
    }

    /** Logins marked as "sign in again", as stored. */
    @NonNull
    static Set<String> load(@NonNull Context context) {
        try {
            Set<String> stored = prefs(context).getStringSet(KEY_REFUSED, null);
            return stored == null ? new HashSet<>() : new HashSet<>(stored);
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /** Stores the marks (a copy: SharedPreferences must never get the set it gave out). */
    static void save(@NonNull Context context, @NonNull Set<String> logins) {
        try {
            prefs(context).edit().putStringSet(KEY_REFUSED, new HashSet<>(logins)).apply();
        } catch (Exception ignored) {
            // the mark stays in memory for this run
        }
    }

    /** GitHub just refused the sign-in of {@code login}: tell the user once, if not in the app. */
    static void refused(@NonNull Context context, @NonNull String login) {
        if (SignInWatch.isAppInFront()) return;
        show(context, login);
    }

    /** A new sign-in of {@code login} (or null: of any account) removes the notification. */
    static void clear(@NonNull Context context, @Nullable String login) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (login != null) manager.cancel(TAG, login.hashCode());
    }

    private static void show(@NonNull Context context, @NonNull String login) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.sign_in_channel),
                NotificationManager.IMPORTANCE_DEFAULT));
        String text = context.getString(R.string.sign_in_notice_text, login);
        // the start screen asks to sign in again (with the account name filled in)
        Intent open = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.sign_in_notice_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(context, login.hashCode(), open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.notify(TAG, login.hashCode(), builder.build());
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
