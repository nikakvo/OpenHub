package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Picks the apps offered for "Open in browser" from what Android answers: never OpenHub
 * itself, never the other GitHub clients that would only open the link in an app again,
 * only activities other apps may start, and one entry per app (a browser often has an
 * activity and an alias for the same link).
 */
public final class BrowserTargets {

    /** One activity that can open the link (package, activity class, exported). */
    public static final class Candidate {
        @NonNull public final String packageName;
        @NonNull public final String activityName;
        public final boolean exported;

        public Candidate(@NonNull String packageName, @NonNull String activityName, boolean exported) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.exported = exported;
        }

        @Override
        public String toString() {
            return packageName + "/" + activityName;
        }
    }

    private BrowserTargets() {
    }

    /** The candidates that may be offered, in the given order, one per app. */
    @NonNull
    public static List<Candidate> pick(@NonNull List<Candidate> all, @NonNull String ourPackage,
                                       @Nullable Collection<String> ignoredPackages) {
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : all) {
            if (!c.exported || ourPackage.equals(c.packageName)) continue;
            if (ignoredPackages != null && ignoredPackages.contains(c.packageName)) continue;
            if (seen.add(c.packageName)) out.add(c);
        }
        return out;
    }

    /**
     * True when the activity Android would start for a plain link is a real app and not
     * OpenHub or the system's own app chooser.
     */
    public static boolean isDirectTarget(@Nullable String packageName, @Nullable String activityName,
                                         @NonNull String ourPackage) {
        if (packageName == null || packageName.isEmpty() || ourPackage.equals(packageName)) return false;
        if ("android".equals(packageName) || packageName.endsWith(".intentresolver")) return false;
        return activityName == null || !activityName.endsWith("ResolverActivity");
    }
}
