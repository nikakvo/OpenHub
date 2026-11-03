package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.ui.activity.LoginActivity;
import com.thirtydegreesray.openhub.ui.activity.SplashActivity;
import com.thirtydegreesray.openhub.ui.widget.SignInPrompt;

import java.lang.ref.WeakReference;

/**
 * Notices, for every screen at once, that GitHub no longer accepts the signed-in account:
 * the HTTP layer reports each 401 of the GitHub API here, and the screen in front shows
 * "Sign in again". The screen itself handles the failed request as any other error (its
 * loading circle stops); the account is never removed.
 */
public final class SignInWatch implements Application.ActivityLifecycleCallbacks {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumed = new WeakReference<>(null);

    private SignInWatch() {
    }

    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new SignInWatch());
    }

    /**
     * The GitHub API answered 401 to a request made with {@code token} (any thread).
     * Only the token of the selected account counts: a request of another account, or one
     * still running with a replaced token, is ignored.
     */
    public static void report(@Nullable String token) {
        AuthUser user = AppData.INSTANCE.getAuthUser();
        if (user == null || StringUtils.isBlank(token) || !token.equals(user.getAccessToken())) return;
        final String login = user.getLoginId();
        MAIN.post(() -> {
            Activity activity = resumed.get();
            // the start and sign-in screens deal with the sign-in themselves
            if (activity == null || activity instanceof SplashActivity
                    || activity instanceof LoginActivity) return;
            SignInPrompt.show(activity, login, false, null);
        });
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        resumed = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
