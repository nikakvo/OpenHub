package com.thirtydegreesray.openhub.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.Notification;
import com.thirtydegreesray.openhub.mvp.model.NotificationSubject;

/**
 * Turns the API address of a notification's subject into the github.com page it is
 * about, and names the reason it was sent. Shared by the inbox and the background check.
 */
public final class NotificationLinks {

    private static final String API = "https://api.github.com/repos/";
    private static final String WEB = "https://github.com/";

    private NotificationLinks() {
    }

    /**
     * github.com address of the subject: issues and pull requests by number, commits by
     * sha; releases, discussions and checks go to the matching page of the repository.
     */
    @NonNull
    public static String htmlUrl(@Nullable String type, @Nullable String apiUrl, @NonNull String repoFullName) {
        String repoPage = WEB + repoFullName;
        if (type == null) type = "";
        if (apiUrl != null && apiUrl.startsWith(API)) {
            String rest = apiUrl.substring(API.length());
            switch (type) {
                case "Issue":
                case "PullRequest":
                case "Commit":
                    return WEB + rest.replace("/pulls/", "/pull/").replace("/commits/", "/commit/");
                default:
                    break;
            }
        }
        switch (type) {
            case "Release":
                return repoPage + "/releases";
            case "Discussion":
                return repoPage + "/discussions";
            case "CheckSuite":
                return repoPage + "/actions";
            case "RepositoryVulnerabilityAlert":
            case "RepositoryDependabotAlertsThread":
                return repoPage + "/security";
            case "RepositoryInvitation":
                return repoPage + "/invitations";
            default:
                return repoPage;
        }
    }

    @NonNull
    public static String htmlUrl(@NonNull Notification notification) {
        NotificationSubject subject = notification.getSubject();
        String type = subject == null || subject.getType() == null ? null : subject.getType().name();
        return htmlUrl(type, subject == null ? null : subject.getUrl(),
                notification.getRepository().getFullName());
    }

    /** "review requested", "mentioned"... or null when there is nothing worth showing. */
    @Nullable
    public static String reason(@NonNull Context context, @Nullable String reason) {
        if (reason == null) return null;
        switch (reason) {
            case "assign":
                return context.getString(R.string.notification_reason_assign);
            case "mention":
                return context.getString(R.string.notification_reason_mention);
            case "team_mention":
                return context.getString(R.string.notification_reason_team_mention);
            case "review_requested":
                return context.getString(R.string.notification_reason_review_requested);
            case "author":
                return context.getString(R.string.notification_reason_author);
            case "comment":
                return context.getString(R.string.notification_reason_comment);
            case "state_change":
                return context.getString(R.string.notification_reason_state_change);
            case "ci_activity":
                return context.getString(R.string.notification_reason_ci_activity);
            case "security_alert":
                return context.getString(R.string.notification_reason_security_alert);
            case "invitation":
                return context.getString(R.string.notification_reason_invitation);
            case "approval_requested":
                return context.getString(R.string.notification_reason_approval_requested);
            default:
                return null;
        }
    }

    /** The API value of a parsed reason, for {@link #reason(Context, String)}. */
    @Nullable
    public static String reasonKey(@Nullable Notification.Type type) {
        if (type == null) return null;
        switch (type) {
            case Assign: return "assign";
            case Author: return "author";
            case Comment: return "comment";
            case Invitation: return "invitation";
            case Mention: return "mention";
            case ReviewRequested: return "review_requested";
            case StateChange: return "state_change";
            case TeamMention: return "team_mention";
            case CiActivity: return "ci_activity";
            case SecurityAlert: return "security_alert";
            case ApprovalRequested: return "approval_requested";
            default: return null;
        }
    }
}
