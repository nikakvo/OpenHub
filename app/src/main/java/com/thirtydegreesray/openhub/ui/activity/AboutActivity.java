

package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.thirtydegreesray.openhub.BuildConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.EdgeToEdge;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ThemeHelper;
import com.thirtydegreesray.openhub.util.WindowUtil;

/**
 * About screen: app info, license and credits. Plain layout in the app's own theme
 * (replaces material-about-library).
 *
 * Created by ThirtyDegreesRay on 2017/8/29.
 */

public class AboutActivity extends AppCompatActivity {

    public static void show(@NonNull Context context){
        Intent intent = new Intent(context, AboutActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeHelper.getTheme(PrefUtils.getTheme(), PrefUtils.getAccentColor()));
        AppUtils.updateAppLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        EdgeToEdge.apply(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.about);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewGroup appItems = findViewById(R.id.about_app_items);
        View titleRow = addItem(appItems, R.drawable.ic_logo, getString(R.string.app_github_name),
                getString(R.string.about_build_by), null);
        ImageView logo = titleRow.findViewById(R.id.about_item_icon);
        int logoSize = WindowUtil.dipToPx(this, 40);
        logo.getLayoutParams().width = logoSize;
        logo.getLayoutParams().height = logoSize;
        addItem(appItems, R.drawable.ic_menu_about, getString(R.string.version),
                BuildConfig.VERSION_NAME, null);
        addItem(appItems, R.drawable.ic_github, getString(R.string.about_source_code),
                "nikakvo/OpenHub",
                v -> RepositoryActivity.show(this, "nikakvo", "OpenHub"));
        addItem(appItems, R.drawable.ic_code, getString(R.string.about_license),
                getString(R.string.about_license_name),
                v -> AppOpener.openInCustomTabsOrBrowser(this, getString(R.string.about_license_url)));

        ViewGroup creditItems = findViewById(R.id.about_credit_items);
        // Original author of OpenHub.
        addItem(creditItems, R.drawable.ic_github, getString(R.string.author_name),
                getString(R.string.about_credit_original),
                v -> RepositoryActivity.show(this, getString(R.string.author_login_id),
                        getString(R.string.app_github_name)));
        // Maintained fork this build is based on.
        addItem(creditItems, R.drawable.ic_github, getString(R.string.about_fork_owner),
                getString(R.string.about_credit_fork),
                v -> RepositoryActivity.show(this, getString(R.string.about_fork_owner),
                        getString(R.string.app_github_name)));
    }

    private View addItem(@NonNull ViewGroup parent, @DrawableRes int icon, @NonNull String title,
                         @NonNull String subtitle, @Nullable View.OnClickListener onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_about, parent, false);
        ((ImageView) row.findViewById(R.id.about_item_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.about_item_title)).setText(title);
        ((TextView) row.findViewById(R.id.about_item_subtitle)).setText(subtitle);
        if (onClick != null) {
            row.setOnClickListener(onClick);
        } else {
            row.setClickable(false);
            row.setBackground(null);
        }
        parent.addView(row);
        return row;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
