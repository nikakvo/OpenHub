package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

/**
 * The tags of a repository as GitHub's GraphQL API answers them (repository.refs with
 * the prefix refs/tags/): what one row of the Tags screen shows, the query that finds the
 * release of each tag, and the links of a tag. Pure Java, so it is unit tested.
 */
public final class TagRows {

    private TagRows() {
    }

    /** Tags per request (GitHub allows 100; 50 keeps the release query small). */
    public static final int PER_PAGE = 50;

    /** An annotated tag may point at another tag; GitHub's own pages follow at most a few. */
    private static final int MAX_TAG_DEPTH = 3;

    private static final String COMMIT_FIELDS =
            "__typename oid abbreviatedOid ... on Commit { committedDate messageHeadline }";

    /** Newest commit first, like the tags page of github.com. */
    public static final String TAGS_QUERY =
            "query($owner: String!, $repo: String!, $after: String, $q: String) {"
            + " repository(owner: $owner, name: $repo) { viewerPermission"
            + " refs(refPrefix: \"refs/tags/\", first: " + PER_PAGE + ", after: $after, query: $q,"
            + " orderBy: {field: TAG_COMMIT_DATE, direction: DESC}) {"
            + " totalCount pageInfo { hasNextPage endCursor }"
            + " nodes { name target { " + COMMIT_FIELDS
            + " ... on Tag { message tagger { date } target { " + COMMIT_FIELDS
            + " ... on Tag { target { " + COMMIT_FIELDS
            + " ... on Tag { target { " + COMMIT_FIELDS + " } } } } } } } } } } }";

    /** One tag as a row. */
    public static final class Entry {
        @NonNull public final String name;
        /** the commit the tag points at; "" when it points at a tree or a file */
        @NonNull public final String commitOid;
        @NonNull public final String shortOid;
        /** a tag object with its own message and tagger (git tag -a) */
        public final boolean annotated;
        /** first line of the tag message, otherwise of the commit message */
        @NonNull public final String headline;
        /** tagger date of an annotated tag, otherwise the commit date (ISO 8601, may be "") */
        @NonNull public final String date;

        Entry(@NonNull String name, @NonNull String commitOid, @NonNull String shortOid,
              boolean annotated, @NonNull String headline, @NonNull String date) {
            this.name = name;
            this.commitOid = commitOid;
            this.shortOid = shortOid;
            this.annotated = annotated;
            this.headline = headline;
            this.date = date;
        }
    }

    /** One node of repository.refs; null for a node without a name. */
    @Nullable
    public static Entry parse(@Nullable JsonObject node) {
        if (node == null) return null;
        String name = Json.str(node, "name");
        if (name.isEmpty()) return null;
        JsonObject target = Json.obj(node, "target");
        boolean annotated = false;
        String tagMessage = "";
        String tagDate = "";
        int depth = 0;
        while (target != null && "Tag".equals(Json.str(target, "__typename")) && depth < MAX_TAG_DEPTH) {
            if (depth == 0) {
                annotated = true;
                tagMessage = firstLine(Json.str(target, "message"));
                tagDate = Json.str(target, "tagger.date");
            }
            target = Json.obj(target, "target");
            depth++;
        }
        String oid = "";
        String shortOid = "";
        String commitHeadline = "";
        String commitDate = "";
        if (target != null && "Commit".equals(Json.str(target, "__typename"))) {
            oid = Json.str(target, "oid");
            shortOid = Json.str(target, "abbreviatedOid");
            if (shortOid.isEmpty() && oid.length() >= 7) shortOid = oid.substring(0, 7);
            commitHeadline = Json.str(target, "messageHeadline");
            commitDate = Json.str(target, "committedDate");
        }
        String headline = tagMessage.isEmpty() ? commitHeadline : tagMessage;
        String date = annotated && !tagDate.isEmpty() ? tagDate : commitDate;
        return new Entry(name, oid, shortOid, annotated, headline, date);
    }

    /**
     * A tagger date is a git timestamp with the tagger's own zone ("...+02:00"), a commit
     * date ends in "Z"; both are read here. Null when empty or unreadable.
     */
    @Nullable
    public static Date date(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return Date.from(OffsetDateTime.parse(iso).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    static String firstLine(@NonNull String text) {
        String trimmed = text.trim();
        int end = trimmed.indexOf('\n');
        String line = end < 0 ? trimmed : trimmed.substring(0, end);
        return line.trim();
    }

    /** People who may delete tags and make releases (GraphQL viewerPermission). */
    public static boolean canWrite(@Nullable String viewerPermission) {
        return "ADMIN".equals(viewerPermission) || "MAINTAIN".equals(viewerPermission)
                || "WRITE".equals(viewerPermission);
    }

    /**
     * Asks for the release of every tag in one request: r0 for tags.get(0), r1 ... The
     * names go in as variables t0, t1 ..., never into the query text.
     */
    @NonNull
    public static String releasesQuery(int count) {
        StringBuilder head = new StringBuilder("query($owner: String!, $repo: String!");
        StringBuilder body = new StringBuilder(" { repository(owner: $owner, name: $repo) {");
        for (int i = 0; i < count; i++) {
            head.append(", $t").append(i).append(": String!");
            body.append(" r").append(i).append(": release(tagName: $t").append(i)
                    .append(") { databaseId name isDraft isPrerelease isLatest immutable }");
        }
        return head.append(")").append(body).append(" } }").toString();
    }

    /** What kind of release a tag has. */
    public enum Kind { NONE, LATEST, RELEASE, PRE_RELEASE, DRAFT }

    @NonNull
    public static Kind kind(@Nullable JsonObject release) {
        if (release == null) return Kind.NONE;
        if (Json.bool(release, "isDraft")) return Kind.DRAFT;
        if (Json.bool(release, "isPrerelease")) return Kind.PRE_RELEASE;
        if (Json.bool(release, "isLatest")) return Kind.LATEST;
        return Kind.RELEASE;
    }

    /** The release of tags.get(index) from the answer of {@link #releasesQuery}. */
    @Nullable
    public static JsonObject release(@Nullable JsonObject repository, int index) {
        JsonElement element = Json.get(repository, "r" + index);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /**
     * The tag below this one in a list that is sorted newest first: what "Compare with the
     * previous tag" compares against. Null for the last row.
     */
    @Nullable
    public static String previous(@NonNull List<String> names, @NonNull String name) {
        int index = names.indexOf(name);
        return index < 0 || index + 1 >= names.size() ? null : names.get(index + 1);
    }

    /** Source code archive of a tag (zip or tar.gz), like the zipball_url of a release. */
    @NonNull
    public static String archiveUrl(@NonNull String owner, @NonNull String repo, @NonNull String tag,
                                    boolean zip) {
        return "https://api.github.com/repos/" + owner + "/" + repo + "/" + (zip ? "zipball" : "tarball")
                + "/" + GitHubLinks.encodePath(tag);
    }

    /** "repo-v1.0.zip"; a "/" in a tag name would make a folder, so it becomes "-". */
    @NonNull
    public static String archiveName(@NonNull String repo, @NonNull String tag, boolean zip) {
        String safe = tag.replace('/', '-').replace('\\', '-');
        return repo + "-" + safe + (zip ? ".zip" : ".tar.gz");
    }

    /** github.com shows a tag (and its release, if there is one) at releases/tag/NAME. */
    @NonNull
    public static String browserUrl(@NonNull String owner, @NonNull String repo, @NonNull String tag) {
        return "https://github.com/" + owner + "/" + repo + "/releases/tag/" + GitHubLinks.encodePath(tag);
    }

    /** The tag list of github.com. */
    @NonNull
    public static String tagsUrl(@NonNull String owner, @NonNull String repo) {
        return "https://github.com/" + owner + "/" + repo + "/tags";
    }
}
