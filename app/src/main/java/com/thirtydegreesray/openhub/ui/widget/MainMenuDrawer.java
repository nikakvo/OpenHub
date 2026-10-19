package com.thirtydegreesray.openhub.ui.widget;

import android.content.Intent;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.ui.activity.MainActivity;
import com.thirtydegreesray.openhub.util.DrawerIcons;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * The main menu (side menu of the start screen) on every other screen: a ≡ button next to
 * Back opens it, Back closes it, and an item goes straight there (the start screen comes
 * back to the front and the screens opened on top of it are closed).
 */
public final class MainMenuDrawer {

    /** Nav item id the start screen opens when it is brought back by this menu. */
    public static final String EXTRA_NAV_ID = "mainMenuNavId";

    private MainMenuDrawer() {
    }

    public static void install(@NonNull AppCompatActivity activity, @NonNull Toolbar toolbar) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1) return;
        View root = content.getChildAt(0);

        // the screen's own layout goes into a drawer; insets are handled as before
        DrawerLayout drawer = new DrawerLayout(activity);
        drawer.setFitsSystemWindows(root.getFitsSystemWindows());
        content.removeView(root);
        drawer.addView(root, new DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        NavigationView nav = new NavigationView(activity);
        nav.setFitsSystemWindows(root.getFitsSystemWindows());
        nav.setItemTextColor(ContextCompat.getColorStateList(activity, R.color.main_drawer_content_color));
        nav.setItemIconTintList(ContextCompat.getColorStateList(activity, R.color.main_drawer_content_color));
        nav.inflateMenu(R.menu.activity_main_drawer);
        Menu menu = nav.getMenu();
        menu.setGroupVisible(R.id.my_account, false);
        menu.setGroupVisible(R.id.manage_accounts, false);
        fillHeader(activity, nav.inflateHeaderView(R.layout.nav_header_main));
        DrawerIcons.apply(nav);
        drawer.addView(nav, new DrawerLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, GravityCompat.START));
        content.addView(drawer);

        nav.setNavigationItemSelectedListener(item -> {
            drawer.closeDrawer(GravityCompat.START, false);
            Intent intent = new Intent(activity, MainActivity.class)
                    .putExtra(EXTRA_NAV_ID, item.getItemId())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            return false;
        });

        // only the ≡ button opens it: an edge swipe would fight with tabs and wide code
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        OnBackPressedCallback back = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawer.closeDrawer(GravityCompat.START);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                back.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                back.setEnabled(false);
            }
        });

        addButton(activity, toolbar, () -> drawer.openDrawer(GravityCompat.START));
    }

    /** The ≡ icon in the toolbar's logo place: right after Back, before the title. */
    private static void addButton(@NonNull AppCompatActivity activity, @NonNull Toolbar toolbar,
                                  @NonNull Runnable open) {
        DrawerArrowDrawable icon = new DrawerArrowDrawable(toolbar.getContext());
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setLogo(icon);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        } else {
            toolbar.setLogo(icon);
        }
        ImageView button = null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ImageView && ((ImageView) child).getDrawable() == icon) {
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
        button.setContentDescription(activity.getString(R.string.main_menu));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> open.run());
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
