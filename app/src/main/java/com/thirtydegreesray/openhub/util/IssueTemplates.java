package com.thirtydegreesray.openhub.util;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.IssueService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Issue templates of a repository, like "New issue" on github.com offers them:
 * Markdown templates (front matter name / about / title / labels / assignees, then the body)
 * and issue forms (.yml), whose fields become "### Label" sections of the body.
 * Read from .github/ISSUE_TEMPLATE/ and the single-file .github/ISSUE_TEMPLATE.md.
 */
public final class IssueTemplates {

    public static final class Template {
        @NonNull public String name = "";
        @NonNull public String about = "";
        @NonNull public String title = "";
        @NonNull public String body = "";
        @NonNull public final List<String> labels = new ArrayList<>();
        @NonNull public final List<String> assignees = new ArrayList<>();
    }

    private IssueTemplates() {
    }

    /** Blocking; an empty list when the repository has none or they cannot be read. */
    @NonNull
    public static List<Template> load(@NonNull String owner, @NonNull String repo) {
        List<Template> templates = new ArrayList<>();
        IssueService service = GitHubApi.service(IssueService.class);
        try {
            JsonElement folder = GitHubApi.call(service.getContents(owner, repo, ".github/ISSUE_TEMPLATE"));
            if (folder != null && folder.isJsonArray()) {
                List<String> paths = new ArrayList<>();
                for (JsonElement element : folder.getAsJsonArray()) {
                    JsonObject entry = element.getAsJsonObject();
                    String name = Json.str(entry, "name").toLowerCase();
                    if (!"file".equals(Json.str(entry, "type"))) continue;
                    if (name.equals("config.yml") || name.equals("config.yaml")) continue;
                    if (name.endsWith(".md") || name.endsWith(".yml") || name.endsWith(".yaml")) {
                        paths.add(Json.str(entry, "path"));
                    }
                }
                Collections.sort(paths, String.CASE_INSENSITIVE_ORDER);
                for (String path : paths) {
                    String text = file(service, owner, repo, path);
                    if (text == null) continue;
                    Template template = path.toLowerCase().endsWith(".md") ? markdown(text) : form(text);
                    if (template.name.isEmpty()) template.name = baseName(path);
                    templates.add(template);
                }
            }
        } catch (Exception ignored) {
            // no folder (404) or not readable
        }
        if (templates.isEmpty()) {
            try {
                String text = file(service, owner, repo, ".github/ISSUE_TEMPLATE.md");
                if (text != null) {
                    Template template = markdown(text);
                    if (template.name.isEmpty()) template.name = "Issue template";
                    templates.add(template);
                }
            } catch (Exception ignored) {
            }
        }
        return templates;
    }

    @Nullable
    private static String file(@NonNull IssueService service, @NonNull String owner, @NonNull String repo,
                               @NonNull String path) throws Exception {
        JsonElement element = GitHubApi.call(service.getContents(owner, repo, encodePath(path)));
        if (element == null || !element.isJsonObject()) return null;
        String content = Json.str(element.getAsJsonObject(), "content");
        if (content.isEmpty()) return null;
        byte[] data = Base64.decode(content.replace("\n", ""), Base64.DEFAULT);
        return new String(data, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @NonNull
    private static String encodePath(@NonNull String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (out.length() > 0) out.append('/');
            out.append(android.net.Uri.encode(part));
        }
        return out.toString();
    }

    @NonNull
    private static String baseName(@NonNull String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('_', ' ').replace('-', ' ');
    }

    // ---------------------------------------------------------------------------------
    // Markdown templates
    // ---------------------------------------------------------------------------------

    @NonNull
    static Template markdown(@NonNull String text) {
        Template template = new Template();
        String body = text;
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---", 4);
            if (end > 0) {
                String front = text.substring(4, end);
                int after = text.indexOf('\n', end + 4);
                body = after < 0 ? "" : text.substring(after + 1);
                String[] lines = front.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.startsWith(" ") || line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    switch (key) {
                        case "name": template.name = unquote(value); break;
                        case "about": template.about = unquote(value); break;
                        case "title": template.title = unquote(value); break;
                        case "labels": i = list(lines, i, value, template.labels); break;
                        case "assignees": i = list(lines, i, value, template.assignees); break;
                        default: break;
                    }
                }
            }
        }
        template.body = body.trim().isEmpty() ? "" : body.replaceFirst("^\n+", "");
        return template;
    }

    // ---------------------------------------------------------------------------------
    // Issue forms
    // ---------------------------------------------------------------------------------

    /**
     * A light reading of an issue form: top-level name / description / title / labels /
     * assignees, and for each body element its type, label and options.
     */
    @NonNull
    static Template form(@NonNull String text) {
        Template template = new Template();
        String[] lines = text.split("\n");
        StringBuilder body = new StringBuilder();
        boolean inBody = false;
        String type = null;
        String label = null;
        String value = null;
        List<String> options = new ArrayList<>();
        int bodyIndent = -1;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int indent = raw.length() - raw.replaceAll("^\\s+", "").length();
            if (indent == 0) {
                if (inBody) {
                    appendField(body, type, label, value, options);
                    type = null;
                    label = null;
                    value = null;
                    options.clear();
                }
                inBody = false;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim();
                String top = line.substring(colon + 1).trim();
                switch (key) {
                    case "name": template.name = unquote(top); break;
                    case "description": template.about = unquote(top); break;
                    case "title": template.title = unquote(top); break;
                    case "labels": i = list(lines, i, top, template.labels); break;
                    case "assignees": i = list(lines, i, top, template.assignees); break;
                    case "body": inBody = true; break;
                    default: break;
                }
                continue;
            }
            if (!inBody) continue;
            // a new element: "- type: textarea" at the body's list indentation
            if (line.startsWith("- type:") && (bodyIndent < 0 || indent <= bodyIndent)) {
                bodyIndent = indent;
                appendField(body, type, label, value, options);
                type = unquote(line.substring("- type:".length()).trim());
                label = null;
                value = null;
                options.clear();
                continue;
            }
            if (line.startsWith("label:") && label == null) {
                label = unquote(line.substring("label:".length()).trim());
            } else if (line.startsWith("value:") && value == null && !"markdown".equals(type)) {
                String v = line.substring("value:".length()).trim();
                // a one-line default; block values ("|") are left out
                if (!v.equals("|") && !v.equals(">") && !v.startsWith("|") && !v.startsWith(">")) value = unquote(v);
            } else if (line.startsWith("options:")) {
                String inline = line.substring("options:".length()).trim();
                if (!inline.isEmpty()) {
                    options.addAll(split(inline));
                } else {
                    int j = i + 1;
                    int optionIndent = -1;
                    for (; j < lines.length; j++) {
                        String next = lines[j].trim();
                        if (next.isEmpty()) continue;
                        int nextIndent = lines[j].length() - lines[j].replaceAll("^\\s+", "").length();
                        if (optionIndent < 0) optionIndent = nextIndent;
                        // the options end where the indentation goes back (the next field)
                        if (!next.startsWith("- ") || nextIndent < optionIndent) break;
                        String option = next.substring(2).trim();
                        if (option.startsWith("label:")) option = option.substring("label:".length()).trim();
                        options.add(unquote(option));
                        // checkbox options may carry "required: true" on the following line
                        while (j + 1 < lines.length && !lines[j + 1].trim().startsWith("- ")
                                && lines[j + 1].trim().startsWith("required")) j++;
                    }
                    i = j - 1;
                }
            }
        }
        if (inBody) appendField(body, type, label, value, options);
        template.body = body.toString().trim();
        if (!template.body.isEmpty()) template.body += "\n";
        return template;
    }

    private static void appendField(@NonNull StringBuilder body, @Nullable String type, @Nullable String label,
                                    @Nullable String value, @NonNull List<String> options) {
        if (type == null || "markdown".equals(type)) return; // text for the reader, not part of the issue
        body.append("### ").append(label == null || label.isEmpty() ? type : label).append("\n\n");
        if (value != null && !value.isEmpty()) body.append(value).append('\n');
        if ("checkboxes".equals(type)) {
            for (String option : options) body.append("- [ ] ").append(option).append('\n');
        } else if ("dropdown".equals(type) && !options.isEmpty()) {
            body.append("<!-- one of: ").append(String.join(", ", options)).append(" -->\n");
        }
        body.append('\n');
    }

    // ---------------------------------------------------------------------------------
    // YAML bits
    // ---------------------------------------------------------------------------------

    /** "a, b", "[a, b]", "a" or a block of "- a" lines; returns the last line used. */
    private static int list(@NonNull String[] lines, int index, @NonNull String value, @NonNull List<String> out) {
        if (!value.isEmpty()) {
            out.addAll(split(value));
            return index;
        }
        int j = index + 1;
        for (; j < lines.length; j++) {
            String next = lines[j].trim();
            if (next.isEmpty()) continue;
            if (!next.startsWith("- ")) break;
            String item = unquote(next.substring(2).trim());
            if (!item.isEmpty()) out.add(item);
        }
        return j - 1;
    }

    @NonNull
    private static List<String> split(@NonNull String value) {
        String v = value.trim();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1);
        List<String> out = new ArrayList<>();
        for (String part : v.split(",")) {
            String item = unquote(part.trim());
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    @NonNull
    private static String unquote(@NonNull String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.equals("''") || v.equals("\"\"") ? "" : v;
    }
}
