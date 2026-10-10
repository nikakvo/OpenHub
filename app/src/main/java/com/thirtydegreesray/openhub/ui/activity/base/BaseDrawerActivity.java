package com.thirtydegreesray.openhub.ui.activity.base;

import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import android.view.MenuItem;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.util.ViewUtils;
import android.view.View;


/**
 * Created by ThirtyDegreesRay on 2017/11/9 12:50:41
 */

public abstract class BaseDrawerActivity<P extends IBaseContract.Presenter> extends BaseActivity<P>
        implements IBaseContract.View {

    @Nullable protected NavigationView navViewStart;
    @Nullable protected NavigationView navViewEnd;
    @Nullable protected DrawerLayout drawerLayout;

    private boolean startDrawerEnable = false;
    private boolean endDrawerEnable = false;

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        if (drawerLayout == null) return;
        initStartDrawerView();
        initEndDrawerView();
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                invalidateBack();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                invalidateBack();
            }
        });
    }

    private void initEndDrawerView() {
        if (navViewEnd == null) return;
        if (endDrawerEnable) {
            navViewEnd.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    return BaseDrawerActivity.this.onNavigationItemSelected(item, false);
                }
            });
        } else {
            drawerLayout.removeView(navViewEnd);
        }
    }

    private void initStartDrawerView() {
        if (navViewStart == null) return;
        if (startDrawerEnable) {
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout,
                    toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            toggle.syncState();
            navViewStart.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    return BaseDrawerActivity.this.onNavigationItemSelected(item, true);
                }
            });
        } else {
            drawerLayout.removeView(navViewStart);
        }
    }

    protected final boolean isDrawerOpen() {
        return drawerLayout != null && (drawerLayout.isDrawerOpen(GravityCompat.START)
                || drawerLayout.isDrawerOpen(GravityCompat.END));
    }

    @Override
    protected boolean interceptsBack() {
        return super.interceptsBack() || isDrawerOpen();
    }

    @Override
    protected void handleBack() {
        if (isDrawerOpen()) {
            closeDrawer();
        } else {
            super.handleBack();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerLayout != null && item.getItemId() == getEndDrawerToggleMenuItemId()) {
            openDrawer(false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean onNavigationItemSelected(@NonNull final MenuItem item, final boolean isStartDrawer) {
        NavigationView navView = getNavigationView(isStartDrawer);
        boolean isMultiSelect = isStartDrawer ? isStartDrawerMultiSelect() : isEndDrawerMultiSelect();
        if (navView == null) return true;
        if (isMultiSelect) {
            ViewUtils.selectMenuItem(navView.getMenu(), item.getItemId(), true);
        }
        closeDrawer();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                onNavItemSelected(item, isStartDrawer);
            }
        }, 250);
        return !isMultiSelect;
    }

    protected final void openDrawer(boolean isStartDrawer) {
        if (drawerLayout != null)
            drawerLayout.openDrawer(isStartDrawer ? GravityCompat.START : GravityCompat.END);
    }

    protected final void closeDrawer() {
        if (drawerLayout != null) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            if (drawerLayout.isDrawerOpen(GravityCompat.END))
                drawerLayout.closeDrawer(GravityCompat.END);
        }

    }

    private NavigationView getNavigationView(boolean isStartDrawer) {
        return isStartDrawer ? navViewStart : navViewEnd;
    }

    protected void removeStartDrawer() {
        removeDrawer(navViewStart);
    }

    protected void removeEndDrawer() {
        removeDrawer(navViewEnd);
    }

    protected void updateStartDrawerContent(int menuId) {
        updateDrawerContent(navViewStart, menuId);
    }

    protected void updateEndDrawerContent(int menuId) {
        updateDrawerContent(navViewEnd, menuId);
    }

    private void removeDrawer(NavigationView navView) {
        if (drawerLayout != null && navView != null) drawerLayout.removeView(navView);
    }

    private void updateDrawerContent(NavigationView navView, int menuId) {
        if (drawerLayout != null && navView != null) {
            navView.getMenu().clear();
            navView.inflateMenu(menuId);
            if (drawerLayout.indexOfChild(navView) == -1) drawerLayout.addView(navView);
        }
    }

    public void setStartDrawerEnable(boolean startDrawerEnable) {
        this.startDrawerEnable = startDrawerEnable;
    }

    public void setEndDrawerEnable(boolean endDrawerEnable) {
        this.endDrawerEnable = endDrawerEnable;
    }

    protected void onNavItemSelected(@NonNull MenuItem item, boolean isStartDrawer) {

    }

    protected boolean isStartDrawerMultiSelect() {
        return false;
    }

    protected boolean isEndDrawerMultiSelect() {
        return false;
    }

    protected int getEndDrawerToggleMenuItemId() {
        return -1;
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        navViewStart = root.findViewById(R.id.nav_view_start);
        navViewEnd = root.findViewById(R.id.nav_view_end);
        drawerLayout = root.findViewById(R.id.drawer_layout);
    }
}
