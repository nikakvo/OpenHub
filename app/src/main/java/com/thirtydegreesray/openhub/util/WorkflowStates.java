package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What GitHub's workflow {@code state} means for the Workflows screen. Pure Java, tested.
 *
 * <p>States (REST 2026-03-10): active, disabled_manually, disabled_inactivity,
 * disabled_fork, deleted. A deleted workflow (its file is gone, old runs remain) can be
 * neither enabled nor disabled.
 */
public final class WorkflowStates {

    public static final String ACTIVE = "active";
    public static final String DISABLED_MANUALLY = "disabled_manually";
    public static final String DISABLED_INACTIVITY = "disabled_inactivity";
    public static final String DISABLED_FORK = "disabled_fork";
    public static final String DELETED = "deleted";

    /** How a state is shown. */
    public enum Kind { ACTIVE, DISABLED, DELETED, UNKNOWN }

    private WorkflowStates() {
    }

    @NonNull
    public static Kind kind(@Nullable String state) {
        if (ACTIVE.equals(state)) return Kind.ACTIVE;
        if (DELETED.equals(state)) return Kind.DELETED;
        if (state != null && state.startsWith("disabled")) return Kind.DISABLED;
        return Kind.UNKNOWN;
    }

    public static boolean canDisable(@Nullable String state) {
        return ACTIVE.equals(state);
    }

    public static boolean canEnable(@Nullable String state) {
        return kind(state) == Kind.DISABLED;
    }

    /** "ci.yml" from ".github/workflows/ci.yml". */
    @NonNull
    public static String fileName(@Nullable String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * The workflow's page on github.com (its runs), like
     * https://github.com/o/r/actions/workflows/ci.yml. Workflows that do not live in
     * .github/workflows (dynamic ones such as "dynamic/pages/pages-build-deployment") have
     * no such page: then the Actions page of the repository.
     */
    @NonNull
    public static String actionsUrl(@NonNull String owner, @NonNull String repo, @Nullable String path) {
        String base = "https://github.com/" + owner + "/" + repo + "/actions";
        if (path == null || !path.startsWith(".github/workflows/")) return base;
        String file = fileName(path);
        return file.isEmpty() ? base : base + "/workflows/" + GitHubLinks.encodeSegment(file);
    }
}
