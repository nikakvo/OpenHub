package com.thirtydegreesray.openhub.ui.widget;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.google.android.material.navigation.NavigationView;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.ui.activity.MainActivity;
import com.thirtydegreesray.openhub.util.DrawerHeader;
import com.thirtydegreesray.openhub.util.DrawerIcons;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

/**
 * The main menu (side menu of the start screen) on every other screen: a ≡ button next to
 * Back slides it in as a panel over the screen, Back or a tap beside it closes it, and an
 * item goes straight there (the start screen comes back to the front and the screens
 * opened on top of it are closed).
 */
public final class MainMenuDrawer {

    /** Nav item id the start screen opens when it is brought back by this menu. */
    public static final String EXTRA_NAV_ID = "mainMenuNavId";

    private MainMenuDrawer() {
    }

    public static void install(@NonNull AppCompatActivity activity, @NonNull Toolbar toolbar) {
        // a panel over the screen: the screen's own layout (collapsing headers, its own
        // drawers, insets) is never touched
        addButton(activity, toolbar, () -> show(activity));
    }

    private static void show(@NonNull AppCompatActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // made with the activity, so it has the app's theme colors (not the dialog theme's)
        NavigationView nav = new NavigationView(activity);
        nav.setBackgroundColor(ViewUtils.getWindowBackground(activity));
        nav.setItemTextColor(ContextCompat.getColorStateList(activity, R.color.main_drawer_content_color));
        nav.setItemIconTintList(ContextCompat.getColorStateList(activity, R.color.main_drawer_content_color));
        nav.inflateMenu(R.menu.activity_main_drawer);
        Menu menu = nav.getMenu();
        menu.setGroupVisible(R.id.my_account, false);
        menu.setGroupVisible(R.id.manage_accounts, false);
        fillHeader(activity, nav.inflateHeaderView(R.layout.nav_header_main));
        DrawerIcons.apply(nav);
        DrawerHeader.apply(nav);
        nav.setNavigationItemSelectedListener(item -> {
            dialog.dismiss();
            Intent intent = new Intent(activity, MainActivity.class)
                    .putExtra(EXTRA_NAV_ID, item.getItemId())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            return false;
        });
        dialog.setContentView(nav);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            int max = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, metrics));
            int gap = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, metrics));
            int width = Math.min(max, metrics.widthPixels - gap);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START | Gravity.TOP);
            window.setWindowAnimations(R.style.MainMenuAnimation);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.5f);
        }
        dialog.setCanceledOnTouchOutside(true);
        // a turned phone or a closed screen takes the panel with it
        LifecycleEventObserver closer = (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY && dialog.isShowing()) dialog.dismiss();
        };
        activity.getLifecycle().addObserver(closer);
        dialog.setOnDismissListener(d -> activity.getLifecycle().removeObserver(closer));
        dialog.show();
    }

    /**
     * The ≡ button takes the toolbar's navigation place at the very left, where the start
     * screen has it too, so it never moves between screens. Back (when the screen has one)
     * goes right after it, in the logo place, and still does what the screen's Back
     * (home) did before.
     */
    private static void addButton(@NonNull AppCompatActivity activity, @NonNull Toolbar toolbar,
                                  @NonNull Runnable open) {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar == null) return;
        boolean hadBack = (actionBar.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0;
        // the screen's own (themed) back arrow, reused next to the menu button
        Drawable back = hadBack ? toolbar.getNavigationIcon() : null;

        DrawerArrowDrawable menuIcon = new DrawerArrowDrawable(toolbar.getContext());
        actionBar.setHomeAsUpIndicator(menuIcon);
        actionBar.setHomeActionContentDescription(R.string.main_menu);
        actionBar.setDisplayHomeAsUpEnabled(true);
        // replaces the action bar's own listener (it is set once, when the bar is created)
        toolbar.setNavigationOnClickListener(v -> open.run());

        if (back == null) return;
        // Back right next to the menu button (the toolbar keeps 72dp free after the
        // navigation button by default)
        toolbar.setContentInsetStartWithNavigation(0);
        actionBar.setLogo(back);
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        ImageView button = null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ImageView && ((ImageView) child).getDrawable() == back) {
                button = (ImageView) child;
                break;
            }
        }
        if (button == null) return;
        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                activity.getResources().getDisplayMetrics()));
        button.setPadding(pad, pad, pad, pad);
        TypedValue ripple = new TypedValue();
        if (toolbar.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            button.setBackgroundResource(ripple.resourceId);
        }
        button.setContentDescription(activity.getString(R.string.navigate_back));
        button.setClickable(true);
        button.setFocusable(true);
        ImageView anchor = button;
        button.setOnClickListener(v -> {
            // the same "home" item the navigation button sends, so every screen keeps its
            // own Back behavior (transitions, results, confirmations)
            MenuItem home = new PopupMenu(activity, anchor).getMenu()
                    .add(Menu.NONE, android.R.id.home, Menu.NONE, R.string.navigate_back);
            activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, home);
        });
    }

    private static void fillHeader(@NonNull AppCompatActivity activity, @NonNull View header) {
        View toggle = header.findViewById(R.id.toggle_account_bn);
        if (toggle != null) toggle.setVisibility(View.GONE);
        User user = AppData.INSTANCE.getLoggedUser();
        if (user == null) return;
        ImageView avatar = header.findViewById(R.id.avatar);
        TextView name = header.findViewById(R.id.name);
        TextView mail = header.findViewById(R.id.mail);
        GlideApp.with(activity)
                .load(user.getAvatarUrl())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(avatar);
        name.setText(StringUtils.isBlank(user.getName()) ? user.getLogin() : user.getName());
        String joined = user.getCreatedAt() == null ? "" : activity.getString(R.string.joined_at)
                .concat(" ").concat(StringUtils.getDateStr(user.getCreatedAt()));
        mail.setText(StringUtils.isBlank(user.getBio()) ? joined : user.getBio());
    }
}
