

package com.thirtydegreesray.openhub.ui.activity;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.thirtydegreesray.openhub.BuildConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.widget.MatrixRainView;
import com.thirtydegreesray.openhub.ui.widget.MatrixWelcomeView;
import com.thirtydegreesray.openhub.util.AppIcons;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUpdater;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.DrawerIcons;
import com.thirtydegreesray.openhub.util.EdgeToEdge;
import com.thirtydegreesray.openhub.util.MatrixScreen;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ThemeHelper;
import com.thirtydegreesray.openhub.util.WindowUtil;

import java.security.MessageDigest;

/**
 * About screen: app info, updates, security, license and credits. With Settings - Menu
 * header on Matrix rain it gets the rain behind a glowing app icon and the decoding name;
 * tapping the version seven times lets the rain fall over the whole screen.
 *
 * Created by ThirtyDegreesRay on 2017/8/29.
 */

public class AboutActivity extends AppCompatActivity {

    /** Taps on the version that open the easter egg. */
    private static final int EGG_TAPS = 7;
    /** A longer pause between taps starts counting again. */
    private static final long TAP_GAP_MS = 2500;

    public static void show(@NonNull Context context){
        Intent intent = new Intent(context, AboutActivity.class);
        context.startActivity(intent);
    }

    private int taps;
    private long lastTap;
    @Nullable private TextView hint;
    @Nullable private Toast tapToast;
    @Nullable private ObjectAnimator glowAnimator;
    @Nullable private Dialog egg;

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

        View hero = MatrixScreen.install(this, findViewById(R.id.about_root), R.layout.layout_about_hero);
        boolean matrixDark = hero != null && MatrixScreen.isDark(this);
        if (hero != null) setUpHero(hero);

        ViewGroup appItems = findViewById(R.id.about_app_items);
        if (hero == null) {
            View titleRow = addItem(appItems, R.drawable.ic_logo, getString(R.string.app_github_name),
                    getString(R.string.about_build_by), 0, null);
            ImageView logo = titleRow.findViewById(R.id.about_item_icon);
            int logoSize = WindowUtil.dipToPx(this, 40);
            logo.getLayoutParams().width = logoSize;
            logo.getLayoutParams().height = logoSize;
            addItem(appItems, R.drawable.ic_menu_about, getString(R.string.version),
                    BuildConfig.VERSION_NAME, 0, v -> onVersionTap());
        }
        addItem(appItems, R.drawable.ic_github, getString(R.string.about_source_code),
                "nikakvo/OpenHub", 0xFF58A6FF,
                v -> RepositoryActivity.show(this, "nikakvo", "OpenHub"));
        addItem(appItems, R.drawable.ic_update, getString(R.string.about_check_updates),
                getString(R.string.about_check_updates_summary), 0xFF3FB950,
                v -> AppUpdater.check(this, true));
        addItem(appItems, R.drawable.ic_code, getString(R.string.about_license),
                getString(R.string.about_license_name), 0xFFE3B341,
                v -> AppOpener.openInCustomTabsOrBrowser(this, getString(R.string.about_license_url)));
        addItem(appItems, R.drawable.ic_pref_shield, getString(R.string.about_security),
                getString(R.string.about_security_summary), 0xFFF85149,
                v -> AppOpener.openInBrowser(this, getString(R.string.about_security_url)));
        String sha = signingSha256(this);
        if (sha != null) {
            String shortSha = sha.substring(0, 8) + "..." + sha.substring(sha.length() - 6);
            View row = addItem(appItems, R.drawable.ic_pref_key, getString(R.string.about_signing),
                    getString(R.string.about_signing_summary, shortSha), 0xFF39FF7A,
                    v -> AppUtils.copyToClipboard(this, sha));
            if (matrixDark) {
                TextView sub = row.findViewById(R.id.about_item_subtitle);
                sub.setTypeface(Typeface.MONOSPACE);
                sub.setTextColor(0xFF8FD9A8);
            }
        }

        ViewGroup creditItems = findViewById(R.id.about_credit_items);
        // Original author of OpenHub.
        View original = addItem(creditItems, R.drawable.ic_github, getString(R.string.author_name),
                getString(R.string.about_credit_original), 0,
                v -> RepositoryActivity.show(this, getString(R.string.author_login_id),
                        getString(R.string.app_github_name)));
        // Maintained fork this build is based on.
        View fork = addItem(creditItems, R.drawable.ic_github, getString(R.string.about_fork_owner),
                getString(R.string.about_credit_fork), 0,
                v -> RepositoryActivity.show(this, getString(R.string.about_fork_owner),
                        getString(R.string.app_github_name)));
        View self = addItem(creditItems, R.drawable.ic_github, "Tears Burn (nikakvo)",
                getString(R.string.about_credit_this), 0,
                v -> ProfileActivity.show(this, "nikakvo"));

        if (matrixDark) {
            ((CardView) findViewById(R.id.about_app_card)).setCardBackgroundColor(MatrixScreen.CARD);
            CardView credits = findViewById(R.id.about_credit_card);
            credits.setCardBackgroundColor(0xFF0A0F0B);
            TextView creditsTitle = findViewById(R.id.about_credits_title);
            creditsTitle.setText(R.string.about_credits_matrix);
            creditsTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            creditsTitle.setTextColor(0xFF39FF7A);
            // the original author by his GitHub name, without the Chinese display name
            ((TextView) original.findViewById(R.id.about_item_title))
                    .setText(getString(R.string.author_login_id));
            ((TextView) original.findViewById(R.id.about_item_subtitle))
                    .setText(R.string.about_credit_original_short);
            terminalRow(original, 0xFFE3B341);
            terminalRow(fork, 0xFFA371F7);
            terminalRow(self, 0xFF39FF7A);
        }
    }

    /** The glowing icon, the decoding name and the version line at the top. */
    private void setUpHero(@NonNull View hero) {
        ((ImageView) hero.findViewById(R.id.about_icon)).setImageDrawable(AppIcons.drawable(this));
        MatrixWelcomeView title = hero.findViewById(R.id.about_title);
        title.setTextSizeSp(30);
        title.setText(getString(R.string.app_github_name));
        TextView version = hero.findViewById(R.id.about_version_text);
        version.setText(getString(R.string.about_version_line,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        View chip = hero.findViewById(R.id.about_version);
        chip.setContentDescription(getString(R.string.version) + " " + BuildConfig.VERSION_NAME);
        chip.setOnClickListener(v -> onVersionTap());
        MatrixScreen.blink(hero.findViewById(R.id.about_version_cursor));
        hint = hero.findViewById(R.id.about_hint);

        View glow = hero.findViewById(R.id.about_glow);
        if (ValueAnimator.areAnimatorsEnabled()) {
            glowAnimator = ObjectAnimator.ofPropertyValuesHolder(glow,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.08f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.08f));
            glowAnimator.setDuration(1300);
            glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
            glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            glowAnimator.start();
        }
    }

    /** Credits as lines of a terminal: "* name" (the name in its own color) over a note. */
    private static void terminalRow(@NonNull View row, @ColorInt int nameColor) {
        TextView title = row.findViewById(R.id.about_item_title);
        TextView sub = row.findViewById(R.id.about_item_subtitle);
        SpannableString line = new SpannableString("* " + title.getText());
        line.setSpan(new ForegroundColorSpan(nameColor), 2, line.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(line);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextColor(0xFF8A9690);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setTextColor(0xFF8A9690);
        row.findViewById(R.id.about_item_icon).setVisibility(View.GONE);
    }

    private void onVersionTap() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTap > TAP_GAP_MS) taps = 0;
        lastTap = now;
        taps++;
        int left = EGG_TAPS - taps;
        if (left <= 0) {
            taps = 0;
            showHint(null);
            showEgg();
        } else if (taps >= 3) {
            showHint(getResources().getQuantityString(R.plurals.about_egg_taps, left, left));
        }
    }

    private void showHint(@Nullable String text) {
        if (hint != null) {
            hint.setText(text);
            return;
        }
        if (tapToast != null) tapToast.cancel();
        tapToast = null;
        if (text != null) {
            tapToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
            tapToast.show();
        }
    }

    /** The easter egg: the rain over the whole screen, "ACCESS GRANTED". */
    private void showEgg() {
        if (egg != null && egg.isShowing()) return;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_matrix_egg, null);
        dialog.setContentView(root);
        MatrixRainView rain = root.findViewById(R.id.egg_rain);
        rain.setEdgeColumns(0, 0, 0);
        rain.setShade(false);
        rain.setHeader(root);
        MatrixWelcomeView title = root.findViewById(R.id.egg_title);
        title.setTextSizeSp(24);
        title.setText(getString(R.string.about_egg_title));
        root.findViewById(R.id.egg_exit).setOnClickListener(v -> dialog.dismiss());
        root.setOnClickListener(v -> dialog.dismiss());
        egg = dialog;
        dialog.show();
    }

    private View addItem(@NonNull ViewGroup parent, @DrawableRes int icon, @NonNull String title,
                         @NonNull String subtitle, @ColorInt int color,
                         @Nullable View.OnClickListener onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_about, parent, false);
        ImageView iconView = row.findViewById(R.id.about_item_icon);
        iconView.setImageResource(icon);
        // colored like the side menu icons (Settings - Menu icons)
        if (color != 0 && !DrawerIcons.STYLE_PLAIN.equals(DrawerIcons.getStyle(this))) {
            iconView.setColorFilter(color);
        }
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

    /** SHA-256 of the certificate this APK is signed with, in lower case hex. */
    @Nullable
    private static String signingSha256(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return null;
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return null;
            // the history lists the oldest first: the current key is the last one
            byte[] cert = signatures[signatures.length - 1].toByteArray();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xFF));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        if (glowAnimator != null) glowAnimator.cancel();
        if (egg != null) egg.dismiss();
        if (tapToast != null) tapToast.cancel();
        super.onDestroy();
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
