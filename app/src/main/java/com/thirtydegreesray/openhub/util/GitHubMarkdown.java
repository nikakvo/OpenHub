package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.MarkdownService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;

/**
 * The Preview of the Markdown editors: the text as GitHub renders it (POST /markdown, GitHub
 * flavored, with the repository as context so #123, @user and commit hashes become links).
 */
public final class GitHubMarkdown {

    private GitHubMarkdown() {
    }

    /**
     * Blocking; throws when GitHub cannot be reached (the caller falls back to
     * {@link MarkdownHtml#renderComment(String, String)}). A picture uploaded a moment ago is private on
     * GitHub until the text is sent: then the text is rendered on the phone and the
     * picture comes from the phone.
     */
    @WorkerThread
    @NonNull
    public static String preview(@NonNull Context context, @NonNull String text,
                                 @Nullable String repoFullName) throws Exception {
        if (hasLocalPicture(text)) return inlineLocalPictures(context, MarkdownHtml.renderComment(text, repoFullName));
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("mode", "gfm");
        if (repoFullName != null) body.put("context", repoFullName);
        ResponseBody html = GitHubApi.call(GitHubApi.service(MarkdownService.class).render(body));
        return "<div class=\"markdown-body\">" + (html == null ? "" : html.string()) + "</div>";
    }

    private static boolean hasLocalPicture(@NonNull String text) {
        for (String url : Attachments.localUrls()) {
            String type = Attachments.localType(url);
            if (type != null && !Attachments.isVideo(type) && text.contains(url)) return true;
        }
        return false;
    }

    /** Replaces src="url" of pictures uploaded in this run with the file from the phone (data: URI). */
    @NonNull
    private static String inlineLocalPictures(@NonNull Context context, @NonNull String html) {
        for (String url : Attachments.localUrls()) {
            String type = Attachments.localType(url);
            String local = Attachments.localCopy(url);
            if (type == null || Attachments.isVideo(type) || local == null) continue;
            String quoted = "src=\"" + url + "\"";
            if (!html.contains(quoted)) continue;
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(local))) {
                if (in == null) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int read;
                long total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > Attachments.IMAGE_LIMIT) break;
                    out.write(buffer, 0, read);
                }
                if (total > Attachments.IMAGE_LIMIT) continue;
                String data = "data:" + type + ";base64,"
                        + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                html = html.replace(quoted, "src=\"" + data + "\"");
            } catch (Exception ignored) {
                // the phone no longer lets the app read it: GitHub's link stays
            }
        }
        return html;
    }
}
