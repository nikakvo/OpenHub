package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.ReviewDrafts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Review" / "Finish your review": Comment, Approve or Request changes, sent together with
 * the pending line comments of {@link ReviewDrafts}. Used by the pull request screen and
 * the diff screen; the caller sends the request body (with its progress dialog).
 */
public final class ReviewDialog {

    public interface Sender {
        /** POST /pulls/{number}/reviews with {@code data}; clear the drafts when it worked. */
        void send(@NonNull Map<String, Object> data, boolean hasDrafts);
    }

    private ReviewDialog() {
    }

    public static void show(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                            int number, @NonNull String headSha, @NonNull Sender sender) {
        List<JsonObject> drafts = ReviewDrafts.list(context, owner, repo, number);
        boolean hasDrafts = !drafts.isEmpty();
        CharSequence[] choices = {context.getString(R.string.pull_review_comment),
                context.getString(R.string.pull_review_approve),
                context.getString(R.string.pull_review_request_changes)};
        String[] events = {"COMMENT", "APPROVE", "REQUEST_CHANGES"};
        String title = hasDrafts
                ? context.getResources().getQuantityString(R.plurals.review_finish_title, drafts.size(), drafts.size())
                : context.getString(R.string.pull_review);
        new ComposeDialog.Builder(context)
                .title(title)
                .repo(owner + "/" + repo)
                .choices(choices, 0)
                .body(context.getString(R.string.pull_review_hint), null)
                // GitHub: a comment review needs text unless it carries line comments;
                // an approval never does; a change request always does
                .bodyRequired(true, hasDrafts ? new boolean[]{true, true, false} : new boolean[]{false, true, false})
                .positive(context.getString(R.string.pull_review_submit))
                .callback((choice, unused, body) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("event", events[choice]);
                    if (!body.isEmpty()) data.put("body", body);
                    if (hasDrafts) {
                        if (!headSha.isEmpty()) data.put("commit_id", headSha);
                        data.put("comments", ReviewDrafts.toReviewComments(drafts));
                    }
                    sender.send(data, hasDrafts);
                })
                .show();
    }

    /** Asks before throwing the pending comments away. */
    public static void confirmDiscard(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      int number, @NonNull Runnable onDiscarded) {
        int count = ReviewDrafts.count(context, owner, repo, number);
        if (count == 0) return;
        new AlertDialog.Builder(context)
                .setTitle(R.string.review_discard)
                .setMessage(context.getResources().getQuantityString(R.plurals.review_discard_message, count, count))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    ReviewDrafts.clear(context, owner, repo, number);
                    onDiscarded.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
