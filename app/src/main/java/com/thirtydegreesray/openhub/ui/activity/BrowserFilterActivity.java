

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(AppData.INSTANCE.getLoggedUser() != null){
            handleBrowserUri(this, getIntent().getData());
        } else {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.setData(getIntent().getData());
            startActivity(intent);
        }
        finish();
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
