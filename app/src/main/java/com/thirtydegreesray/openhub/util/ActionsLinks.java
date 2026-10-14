package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * github.com links to a workflow run or one of its jobs
 * ({@code /owner/repo/actions/runs/123} and {@code .../runs/123/job/456}), opened in the
 * app. Plain Java (unit tested).
 */
public final class ActionsLinks {

    public final String owner;
    public final String repo;
    public final long runId;
    /** 0 for the run itself */
    public final long jobId;

    private static final Pattern RUN = Pattern.compile(
            "^https?://(?:www\\.)?github\\.com/([^/?#]+)/([^/?#]+)/actions/runs/(\\d+)"
                    + "(?:/attempts/\\d+)?(?:/job/(\\d+))?/?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    private ActionsLinks(String owner, String repo, long runId, long jobId) {
        this.owner = owner;
        this.repo = repo;
        this.runId = runId;
        this.jobId = jobId;
    }

    @Nullable
    public static ActionsLinks parse(@Nullable String url) {
        if (url == null) return null;
        Matcher m = RUN.matcher(url.trim());
        if (!m.matches()) return null;
        try {
            long run = Long.parseLong(m.group(3));
            long job = m.group(4) == null ? 0 : Long.parseLong(m.group(4));
            return new ActionsLinks(m.group(1), m.group(2), run, job);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return owner + "/" + repo + " run " + runId + (jobId != 0 ? " job " + jobId : "");
    }
}
