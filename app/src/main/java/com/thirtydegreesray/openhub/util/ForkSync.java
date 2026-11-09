package com.thirtydegreesray.openhub.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Decisions for "Sync fork" (pure Java, unit tested): which upstream branch a fork branch is
 * compared with and synced from, how GitHub's numbers read, and what GitHub's answer means.
 */
public final class ForkSync {

    private ForkSync() {
    }

    /** How a fork branch stands against its upstream branch. */
    public enum State { UP_TO_DATE, AHEAD, BEHIND, DIVERGED }

    /** GitHub's {@code merge_type} after a sync. */
    public enum Result { FAST_FORWARD, MERGE, NONE, UNKNOWN }

    /**
     * The upstream branch GitHub syncs from: the fork's default branch follows the parent's
     * default branch (they may have different names), any other branch the branch with the
     * same name.
     */
    public static String upstreamBranch(String branch, String forkDefault, String parentDefault) {
        if (branch == null || branch.isEmpty()) return parentDefault;
        if (branch.equals(forkDefault) && parentDefault != null && !parentDefault.isEmpty()) {
            return parentDefault;
        }
        return branch;
    }

    public static State state(int ahead, int behind) {
        if (ahead > 0 && behind > 0) return State.DIVERGED;
        if (ahead > 0) return State.AHEAD;
        if (behind > 0) return State.BEHIND;
        return State.UP_TO_DATE;
    }

    /** "PerformanC/ReZygisk:main", like the label on github.com. */
    public static String label(String parentFullName, String upstreamBranch) {
        return parentFullName + ":" + upstreamBranch;
    }

    /** The owner part of "owner/repo". */
    public static String ownerOf(String fullName) {
        int slash = fullName == null ? -1 : fullName.indexOf('/');
        return slash < 0 ? fullName : fullName.substring(0, slash);
    }

    /**
     * The {@code basehead} for GET repos/{fork}/compare/{basehead}, already encoded:
     * "parentOwner:upstream...branch". ahead_by = commits only in the fork branch,
     * behind_by = new commits upstream.
     */
    public static String basehead(String parentOwner, String upstreamBranch, String branch) {
        return encode(parentOwner) + ":" + encode(upstreamBranch) + "..." + encode(branch);
    }

    public static Result result(String mergeType) {
        if ("fast-forward".equals(mergeType)) return Result.FAST_FORWARD;
        if ("merge".equals(mergeType)) return Result.MERGE;
        if ("none".equals(mergeType)) return Result.NONE;
        return Result.UNKNOWN;
    }

    /** github.com page that compares the upstream branch into the fork branch (conflicts). */
    public static String conflictUrl(String forkFullName, String branch, String parentFullName,
                                     String upstreamBranch) {
        String parentRepo = parentFullName.substring(parentFullName.indexOf('/') + 1);
        return "https://github.com/" + forkFullName + "/compare/" + encode(branch) + "..."
                + encode(ownerOf(parentFullName)) + ":" + encode(parentRepo) + ":" + encode(upstreamBranch);
    }

    /** Percent-encodes a branch name for a URL path; "/" stays (GitHub reads it as part of the ref). */
    public static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20").replace("%2F", "/");
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }
}
