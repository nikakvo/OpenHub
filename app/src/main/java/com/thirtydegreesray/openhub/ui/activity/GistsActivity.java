package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.GistsFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

/**
 * Gists of a user. For the logged in user the menu also has New gist and Starred gists.
 */
public class GistsActivity extends SingleFragmentActivity<IBaseContract.Presenter, GistsFragment> {

    public static void show(@NonNull Activity activity, @NonNull String user) {
        show(activity, user, false);
    }

    private static void show(@NonNull Activity activity, @NonNull String user, boolean starred) {
        activity.startActivity(new Intent(activity, GistsActivity.class)
                .putExtras(BundleHelper.builder().put("user", user).put("starred", starred).build()));
    }

    @AutoAccess String user;
    @AutoAccess boolean starred;

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarTitle(getString(starred ? R.string.gist_starred : R.string.gists), user);
    }

    @Override
    protected GistsFragment createFragment() {
        return GistsFragment.create(user, starred);
    }

    private boolean isMe() {
        return AppData.INSTANCE.getLoggedUser() != null
                && AppData.INSTANCE.getLoggedUser().getLogin().equalsIgnoreCase(user);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isMe()) {
            menu.add(0, 1, 0, R.string.gist_new);
            if (!starred) menu.add(0, 2, 1, R.string.gist_starred);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startActivity(GistEditActivity.newGistIntent(this));
                return true;
            case 2:
                show(this, user, true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
