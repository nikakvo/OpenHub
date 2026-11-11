package com.thirtydegreesray.openhub.util.richtext;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.QuoteStyle;

/** Colors and sizes of {@link RichText}, taken from the screen's theme (dark or light). */
final class RichStyle {

    final float density;
    final int textColor;
    final int secondaryColor;
    final int quoteBar;
    final int quoteBarWidth;
    final int quoteGap;
    /** background of code and of the header row of tables */
    final int codeBackground;
    /** lines of tables and of horizontal rules */
    final int borderColor;
    final boolean dark;
    /** pictures are loaded (Settings: not on mobile data when switched off) */
    final boolean loadImages;
    /** links and pictures react to taps (not in previews that open on a tap) */
    final boolean clickable;

    RichStyle(@NonNull Context context, boolean clickable) {
        this.clickable = clickable;
        density = context.getResources().getDisplayMetrics().density;
        textColor = primaryText(context);
        secondaryColor = QuoteStyle.textColor(context);
        quoteBar = QuoteStyle.barColor(context);
        quoteBarWidth = QuoteStyle.px(context, QuoteStyle.BAR_DP);
        quoteGap = QuoteStyle.px(context, QuoteStyle.GAP_DP);
        codeBackground = withAlpha(textColor, 0x1F);
        borderColor = withAlpha(textColor, 0x40);
        dark = luminance(textColor) > 0.5f;
        loadImages = PrefUtils.isLoadImageEnable();
    }

    /** For tests on the JVM: fixed colors, no theme. */
    RichStyle(float density, @ColorInt int textColor, boolean loadImages, boolean clickable) {
        this.clickable = clickable;
        this.density = density;
        this.textColor = textColor;
        secondaryColor = withAlpha(textColor, 0xB3);
        quoteBar = withAlpha(textColor, 140);
        quoteBarWidth = Math.round(QuoteStyle.BAR_DP * density);
        quoteGap = Math.round(QuoteStyle.GAP_DP * density);
        codeBackground = withAlpha(textColor, 0x1F);
        borderColor = withAlpha(textColor, 0x40);
        dark = luminance(textColor) > 0.5f;
        this.loadImages = loadImages;
    }

    int px(float dp) {
        return Math.round(dp * density);
    }

    /** The color of GitHub's alerts ({@code > [!NOTE]} and the others). */
    @ColorInt
    int alertColor(@NonNull String type) {
        switch (type) {
            case "tip": return dark ? 0xFF3FB950 : 0xFF1A7F37;
            case "important": return dark ? 0xFFAB7DF8 : 0xFF8250DF;
            case "warning": return dark ? 0xFFD29922 : 0xFF9A6700;
            case "caution": return dark ? 0xFFF85149 : 0xFFCF222E;
            default: return dark ? 0xFF4493F8 : 0xFF0969DA;
        }
    }

    @NonNull
    static String alertMark(@NonNull String type) {
        switch (type) {
            case "tip": return "💡 ";
            case "important": return "❗ ";
            case "warning": return "⚠ ";
            case "caution": return "⛔ ";
            default: return "ⓘ ";
        }
    }

    /** Colors of GitHub's syntax highlighting in code blocks (the "pl-*" classes). */
    @Nullable
    Integer codeColor(@NonNull String cls) {
        switch (cls) {
            case "pl-c": case "pl-cce":
                return dark ? 0xFF8B949E : 0xFF59636E;
            case "pl-k":
                return dark ? 0xFFFF7B72 : 0xFFCF222E;
            case "pl-s": case "pl-pds": case "pl-sr":
                return dark ? 0xFFA5D6FF : 0xFF0A3069;
            case "pl-en":
                return dark ? 0xFFD2A8FF : 0xFF6639BA;
            case "pl-c1": case "pl-v": case "pl-mi1":
                return dark ? 0xFF79C0FF : 0xFF0550AE;
            case "pl-ent":
                return dark ? 0xFF7EE787 : 0xFF116329;
            case "pl-smi": case "pl-s1":
                return dark ? 0xFFE6EDF3 : 0xFF1F2328;
            case "pl-e":
                return dark ? 0xFFFFA657 : 0xFF953800;
            case "pl-md":
                return dark ? 0xFFFFA198 : 0xFF82071E;
            case "pl-mi":
                return dark ? 0xFF7EE787 : 0xFF116329;
            default:
                return null;
        }
    }

    @ColorInt
    private static int primaryText(@NonNull Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            ColorStateList list = a.getColorStateList(0);
            return list != null ? list.getDefaultColor() : 0xFF888888;
        } catch (RuntimeException e) {
            return 0xFF888888;
        } finally {
            a.recycle();
        }
    }

    @ColorInt
    private static int withAlpha(@ColorInt int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float luminance(@ColorInt int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }
}
