package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * How the Webhooks screen words GitHub's webhook and delivery answers: event lists, the
 * result of the last delivery, delivery titles and durations. Pure Java, unit tested.
 */
public final class Webhooks {

    private Webhooks() {
    }

    /** Recent deliveries asked for at once (GitHub keeps them for a few days). */
    public static final int DELIVERIES = 50;

    /** What a delivery or the last response of a webhook tells. */
    public enum Result { OK, FAILED, NONE }

    /** "*" = every event; otherwise the names joined. Null when GitHub sent none. */
    @Nullable
    public static String events(@Nullable List<String> events) {
        if (events == null || events.isEmpty()) return null;
        if (events.contains("*")) return "*";
        return String.join(", ", events);
    }

    /**
     * last_response of a webhook: {"code": 200, "status": "active"} after a good delivery,
     * {"code": null, "status": "unused"} before the first one, other codes or a message
     * after a failure.
     */
    @NonNull
    public static Result lastResponse(@Nullable Integer code, @Nullable String status) {
        if ("unused".equals(status) || (code == null && (status == null || status.isEmpty()))) {
            return Result.NONE;
        }
        return code != null && code >= 200 && code < 300 ? Result.OK : Result.FAILED;
    }

    /** A delivery: 2xx answered by the receiver is OK; 0 means GitHub could not connect. */
    @NonNull
    public static Result delivery(int statusCode) {
        return statusCode >= 200 && statusCode < 300 ? Result.OK : Result.FAILED;
    }

    /** "pull_request.opened", "push", "ping". */
    @NonNull
    public static String deliveryTitle(@Nullable String event, @Nullable String action) {
        String e = event == null || event.isEmpty() ? "?" : event;
        return action == null || action.isEmpty() ? e : e + "." + action;
    }

    /** GitHub gives the duration in seconds: "0.42 s", "12 s". */
    @NonNull
    public static String duration(double seconds) {
        if (seconds <= 0) return "0 s";
        if (seconds < 10) return String.format(Locale.ROOT, "%.2f s", seconds);
        return Math.round(seconds) + " s";
    }

    /** The host of a webhook URL, for subtitles; the whole text when it is not a URL. */
    @NonNull
    public static String host(@Nullable String url) {
        if (url == null) return "";
        try {
            String host = new URI(url.trim()).getHost();
            return host == null || host.isEmpty() ? url : host;
        } catch (Exception e) {
            return url;
        }
    }

    /** Long payloads are cut for the screen; the cut is marked. */
    @NonNull
    public static String cut(@Nullable String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "\n… (" + (text.length() - max) + " more characters)";
    }

    /** github.com page of one webhook (its settings and deliveries). */
    @NonNull
    public static String settingsUrl(@NonNull String owner, @NonNull String repo, long hookId) {
        return "https://github.com/" + owner + "/" + repo + "/settings/hooks" + (hookId > 0 ? "/" + hookId : "");
    }
}
