package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Extra facts of a Dependabot alert as GitHub sends them: the version the repository
 * uses, the CVSS 4.0 and 3.x scores, the EPSS exploit chance and reachability. Each one
 * is null when GitHub did not send it, so nothing is shown that is not known.
 * Pure Java, unit tested.
 */
public final class DependabotDetails {

    private DependabotDetails() {
    }

    /** The version or constraint Dependabot recorded; "" = no constraint; null = unknown. */
    @Nullable
    public static String versionRequirement(@Nullable JsonObject alert) {
        JsonElement value = Json.get(alert, "dependency.version_requirement");
        if (value == null || !value.isJsonPrimitive()) return null;
        return value.getAsString().trim();
    }

    /** "8.7" for a CVSS score of an advisory (cvss_v4 / cvss_v3); null when missing or 0. */
    @Nullable
    public static String cvss(@Nullable JsonObject alert, @NonNull String version) {
        JsonElement score = Json.get(alert, "security_advisory.cvss_severities." + version + ".score");
        if (score == null || !score.isJsonPrimitive()) return null;
        double value;
        try {
            value = score.getAsDouble();
        } catch (Exception e) {
            return null;
        }
        if (value <= 0) return null;
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** The CVSS vector of the same block, "" when none. */
    @NonNull
    public static String cvssVector(@Nullable JsonObject alert, @NonNull String version) {
        return Json.str(alert, "security_advisory.cvss_severities." + version + ".vector_string");
    }

    /** EPSS as two numbers for the text: chance in % and percentile in %; null when missing. */
    @Nullable
    public static String[] epss(@Nullable JsonObject alert) {
        JsonElement percentage = Json.get(alert, "security_advisory.epss.percentage");
        JsonElement percentile = Json.get(alert, "security_advisory.epss.percentile");
        if (percentage == null || !percentage.isJsonPrimitive()) return null;
        try {
            double chance = percentage.getAsDouble() * 100;
            String chanceText = chance >= 1 ? String.format(Locale.ROOT, "%.1f", chance)
                    : String.format(Locale.ROOT, "%.2f", chance);
            String rank = percentile != null && percentile.isJsonPrimitive()
                    ? String.valueOf(Math.round(percentile.getAsDouble() * 100)) : null;
            return new String[]{chanceText, rank};
        } catch (Exception e) {
            return null;
        }
    }

    /** reachable, unreachable, potentially_reachable; null when GitHub has no verdict to tell. */
    @Nullable
    public static String reachability(@Nullable JsonObject alert) {
        String verdict = Json.str(alert, "reachability.verdict");
        switch (verdict) {
            case "reachable":
            case "unreachable":
            case "potentially_reachable":
                return verdict;
            default:
                return null;
        }
    }

    /** direct / transitive (unknown and inconclusive are not shown). */
    @Nullable
    public static String relationship(@Nullable JsonObject alert) {
        String value = Json.str(alert, "dependency.relationship");
        return "direct".equals(value) || "transitive".equals(value) ? value : null;
    }

    /** runtime / development. */
    @Nullable
    public static String scope(@Nullable JsonObject alert) {
        String value = Json.str(alert, "dependency.scope");
        return "runtime".equals(value) || "development".equals(value) ? value : null;
    }
}
