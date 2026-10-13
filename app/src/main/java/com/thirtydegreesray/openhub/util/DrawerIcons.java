package com.thirtydegreesray.openhub.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.thirtydegreesray.openhub.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Colored side menu icons, optionally with a soft glow (dark themes) that lights up
 * once each time the menu opens. Settings: Menu icons (Glow / Colored / Plain).
 */
public final class DrawerIcons {

    public static final String PREF = "menuIconStyle";
    public static final String STYLE_GLOW = "glow";
    public static final String STYLE_COLORED = "colored";
    public static final String STYLE_PLAIN = "plain";
    public static final String[] STYLES = {STYLE_GLOW, STYLE_COLORED, STYLE_PLAIN};

    private static final int GLOW_DP = 6;
    private static final int ICON_DP = 24;
    private static final int LIGHT_UP_MS = 600;

    /** Same colors as the GitHub interface: bright on dark themes, deeper on light ones. */
    private static final SparseIntArray DARK = new SparseIntArray();
    private static final SparseIntArray LIGHT = new SparseIntArray();

    static {
        put(R.id.nav_profile, 0xFF58A6FF, 0xFF0969DA);
        put(R.id.nav_notifications, 0xFFF0883E, 0xFFBC4C00);
        put(R.id.nav_news, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_issues, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_gists, 0xFFA371F7, 0xFF8250DF);
        put(R.id.nav_new_repo, 0xFF03A9F4, 0xFF0288D1);
        put(R.id.nav_owned, 0xFF58A6FF, 0xFF0969DA);
        put(R.id.nav_starred, 0xFFE3B341, 0xFF9A6700);
        put(R.id.nav_bookmarks, 0xFFDB61A2, 0xFFBF3989);
        put(R.id.nav_search, 0xFF79C0FF, 0xFF0969DA);
        put(R.id.nav_trace, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_trending, 0xFFF85149, 0xFFCF222E);
        put(R.id.nav_collections, 0xFFD2A8FF, 0xFF8250DF);
        put(R.id.nav_topics, 0xFF56D4DD, 0xFF1B7C83);
        put(R.id.nav_public_news, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_settings, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_help, 0xFF03A9F4, 0xFF0288D1);
        put(R.id.nav_about, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_logout, 0xFFF85149, 0xFFCF222E);
        put(R.id.nav_add_account, 0xFF3FB950, 0xFF1A7F37);
    }

    private static void put(int id, int dark, int light) {
        DARK.put(id, dark);
        LIGHT.put(id, light);
    }

    private DrawerIcons() {
    }

    @NonNull
    public static String getStyle(@NonNull Context context) {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(PREF, STYLE_GLOW);
    }

    /** Colors (and lights up) the icons of the side menu according to the setting. */
    public static void apply(@NonNull NavigationView nav) {
        Context context = nav.getContext();
        String style = getStyle(context);
        if (STYLE_PLAIN.equals(style)) return;
        boolean dark = AppUtils.isNightMode();
        boolean glow = STYLE_GLOW.equals(style) && dark;
        SparseIntArray colors = dark ? DARK : LIGHT;
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().density;
        int iconPx = Math.round(ICON_DP * density);
        int padPx = glow ? Math.round(GLOW_DP * density) : 0;

        nav.setItemIconTintList(null);
        if (glow) {
            // a bigger icon box for the glow, with the icon itself at its usual place and size
            nav.setItemIconSize(iconPx + 2 * padPx);
            nav.setItemHorizontalPadding(Math.max(0, nav.getItemHorizontalPadding() - padPx));
            nav.setItemIconPadding(Math.max(0, nav.getItemIconPadding() - padPx));
        }
        List<BitmapDrawable> glows = new ArrayList<>();
        for (MenuItem item : items(nav.getMenu())) {
            Drawable icon = item.getIcon();
            if (icon == null) continue;
            int color = colors.get(item.getItemId(), colors.get(R.id.nav_profile));
            Drawable tinted = icon.mutate();
            tinted.setTint(color);
            if (!glow) {
                item.setIcon(tinted);
                continue;
            }
            BitmapDrawable halo = new BitmapDrawable(res, halo(tinted, iconPx, padPx, color));
            LayerDrawable layers = new LayerDrawable(new Drawable[]{halo,
                    new InsetDrawable(tinted, padPx)});
            item.setIcon(layers);
            glows.add(halo);
        }
        if (glow) lightUpOnOpen(nav, glows);
    }

    @NonNull
    private static List<MenuItem> items(@NonNull Menu menu) {
        List<MenuItem> list = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            list.add(item);
            SubMenu sub = item.getSubMenu();
            if (sub != null) list.addAll(items(sub));
        }
        return list;
    }

    /** The icon's shape, blurred and colored: a soft light around it. */
    @NonNull
    private static Bitmap halo(@NonNull Drawable icon, int iconPx, int padPx, int color) {
        int size = iconPx + 2 * padPx;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(padPx, padPx, padPx + iconPx, padPx + iconPx);
        icon.draw(canvas);
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        int[] alpha = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) alpha[i] = pixels[i] >>> 24;
        int radius = Math.max(1, padPx / 2);
        for (int pass = 0; pass < 3; pass++) {
            alpha = blur(alpha, size, radius, true);
            alpha = blur(alpha, size, radius, false);
        }
        int rgb = color & 0x00FFFFFF;
        for (int i = 0; i < pixels.length; i++) {
            int a = Math.min(255, alpha[i] * 2); // a little stronger than a plain blur
            pixels[i] = (a << 24) | rgb;
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    /** One box blur pass, horizontal or vertical. */
    @NonNull
    private static int[] blur(@NonNull int[] in, int size, int radius, boolean horizontal) {
        int[] out = new int[in.length];
        int window = radius * 2 + 1;
        for (int line = 0; line < size; line++) {
            int sum = 0;
            for (int k = -radius; k <= radius; k++) sum += at(in, size, line, k, horizontal);
            for (int pos = 0; pos < size; pos++) {
                int index = horizontal ? line * size + pos : pos * size + line;
                out[index] = sum / window;
                sum += at(in, size, line, pos + radius + 1, horizontal) - at(in, size, line, pos - radius, horizontal);
            }
        }
        return out;
    }

    private static int at(@NonNull int[] in, int size, int line, int pos, boolean horizontal) {
        if (pos < 0 || pos >= size) return 0;
        return horizontal ? in[line * size + pos] : in[pos * size + line];
    }

    /** The glow fades in each time the menu opens, once; it stays on afterwards. */
    private static void lightUpOnOpen(@NonNull NavigationView nav, @NonNull List<BitmapDrawable> glows) {
        ViewParent parent = nav.getParent();
        if (!(parent instanceof DrawerLayout)) return;
        ((DrawerLayout) parent).addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private ValueAnimator animator;

            @Override
            public void onDrawerStateChanged(int newState) {
                DrawerLayout drawer = (DrawerLayout) parent;
                if (newState != DrawerLayout.STATE_SETTLING && newState != DrawerLayout.STATE_DRAGGING) return;
                if (drawer.isDrawerVisible(nav)) return; // already open, or closing
                if (animator != null) animator.cancel();
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    setAlpha(glows, 255, nav);
                    return;
                }
                animator = ValueAnimator.ofInt(0, 255);
                animator.setDuration(LIGHT_UP_MS);
                animator.setStartDelay(150);
                animator.addUpdateListener(a -> setAlpha(glows, (int) a.getAnimatedValue(), nav));
                setAlpha(glows, 0, nav);
                animator.start();
            }
        });
    }

    private static void setAlpha(@NonNull List<BitmapDrawable> glows, int alpha, @NonNull View nav) {
        // each change reaches the menu row through the drawable callbacks
        for (BitmapDrawable glow : glows) glow.setAlpha(alpha);
        nav.invalidate();
    }

    /** For the settings summary. */
    public static int label(@NonNull String style) {
        switch (style) {
            case STYLE_COLORED:
                return R.string.menu_icons_colored;
            case STYLE_PLAIN:
                return R.string.menu_icons_plain;
            default:
                return R.string.menu_icons_glow;
        }
    }
}
