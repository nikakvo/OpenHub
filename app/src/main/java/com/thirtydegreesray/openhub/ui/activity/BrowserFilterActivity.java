

package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.util.AppOpener;

/**
 * Created by ThirtyDegreesRay on 2017/10/19 17:31:18
 */

public class BrowserFilterActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(newBase);
        // the app's texts in the app language, the keyboard keeps the phone's languages
        // (AppCompat builds its own configuration from the base, so set it on top)
        applyOverrideConfiguration(com.thirtydegreesray.openhub.util.AppUtils.appLanguageConfiguration());
    }


    /** Opens the notifications inbox (from the background check's summary notification). */
    public static final String NOTIFICATIONS = "notifications";
    /**
     * Opens the Actions screen of a repository (from a workflow run notification):
     * "actions:owner/repo" or "actions:owner/repo:run" when the user may start workflows.
     */
    public static final String ACTIONS = "actions:";

    @NonNull
    public static String actionsUri(@NonNull String fullName, boolean canRun) {
        return ACTIONS + fullName + (canRun ? ":run" : "");
    }

    @Nullable private static String lastLink;
    private static long lastLinkAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
        finish();
    }

    /**
     * Telegram hands the same link over twice within a few milliseconds. Android stacked
     * both in one task: the first opened the page, the second waited below it unstarted
     * and opened the page again on the first Back. With launchMode singleTop the second
     * link reaches this screen here instead, while it is still on top, and is dropped.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
        finish();
    }

    private void handle(@Nullable Intent intent) {
        // exported: other apps may start it with anything, or with nothing
        if (intent == null || intent.getData() == null) return;
        String link = intent.getData().toString();
        long now = android.os.SystemClock.elapsedRealtime();
        if (link.equals(lastLink) && now - lastLinkAt < 3000) return;
        lastLink = link;
        lastLinkAt = now;
        if(AppData.INSTANCE.getLoggedUser() != null){
            handleBrowserUri(this, intent.getData());
        } else {
            Intent splash = new Intent(this, SplashActivity.class);
            splash.setData(intent.getData());
            startActivity(splash);
        }
    }

    public static void handleBrowserUri(@NonNull Activity activity, @NonNull Uri uri){
        //handle shortcuts redirection
        if (uri.toString().equals("trending")){
            activity.startActivity(new Intent(activity, TrendingActivity.class));
        } else if (uri.toString().equals(NOTIFICATIONS)){
            activity.startActivity(new Intent(activity, NotificationsActivity.class));
        } else if (uri.toString().startsWith(ACTIONS)){
            String target = uri.toString().substring(ACTIONS.length());
            boolean canRun = target.endsWith(":run");
            if (canRun) target = target.substring(0, target.length() - ":run".length());
            int slash = target.indexOf('/');
            if (slash > 0) {
                ActionsActivity.show(activity, target.substring(0, slash), target.substring(slash + 1),
                        null, canRun);
            }
        } else if (uri.toString().equals("search")){
            activity.startActivity(new Intent(activity, SearchActivity.class));
        } else {
            AppOpener.launchUrl(activity, uri);
        }
    }

}
