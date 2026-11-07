package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thirtydegreesray.openhub.mvp.model.Issue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Issue types and issue fields: GitHub's colors, the text a value is shown with, the
 * definitions of a repository's fields (GraphQL) and the values sent back (REST). Pure Java.
 */
public final class IssueFields {

    public static final String TEXT = "text";
    public static final String NUMBER = "number";
    public static final String DATE = "date";
    public static final String SINGLE_SELECT = "single_select";
    public static final String MULTI_SELECT = "multi_select";

    /** Repository issue fields with the options of the select fields. */
    public static final String FIELDS_QUERY = "query($owner:String!,$repo:String!){"
            + "repository(owner:$owner,name:$repo){issueFields(first:50){nodes{"
            + "... on IssueFieldText{fullDatabaseId name description dataType}"
            + "... on IssueFieldNumber{fullDatabaseId name description dataType}"
            + "... on IssueFieldDate{fullDatabaseId name description dataType}"
            + "... on IssueFieldSingleSelect{fullDatabaseId name description dataType options{name color description}}"
            + "... on IssueFieldMultiSelect{fullDatabaseId name description dataType options{name color description}}"
            + "}}}}";

    private IssueFields() {
    }

    /** An option of a select field. */
    public static class Option {
        public final String name;
        @Nullable public final String color;
        @Nullable public final String description;

        public Option(String name, @Nullable String color, @Nullable String description) {
            this.name = name;
            this.color = color;
            this.description = description;
        }
    }

    /** A field of the repository's organization. */
    public static class Field {
        public final long id;
        public final String name;
        @Nullable public final String description;
        /** One of the constants above (lower case). */
        public final String dataType;
        public final List<Option> options;

        public Field(long id, String name, @Nullable String description, String dataType, List<Option> options) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.dataType = dataType;
            this.options = options;
        }
    }

    /** An issue type of the repository's organization. */
    public static class Type {
        public final String name;
        @Nullable public final String description;
        @Nullable public final String color;

        public Type(String name, @Nullable String description, @Nullable String color) {
            this.name = name;
            this.description = description;
            this.color = color;
        }
    }

    // ---------------------------------------------------------------- colors

    /** GitHub's color names (any case) as colors that read on dark and light themes. */
    public static int color(@Nullable String name) {
        String c = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        switch (c) {
            case "blue": return 0xFF388BFD;
            case "green": return 0xFF3FB950;
            case "yellow": return 0xFFD29922;
            case "orange": return 0xFFDB6D28;
            case "red": return 0xFFF85149;
            case "pink": return 0xFFDB61A2;
            case "purple": return 0xFFA371F7;
            default: return 0xFF8B949E;
        }
    }

    // ---------------------------------------------------------------- reading

    /** "text", "single_select", ... from GraphQL's TEXT / SINGLE_SELECT or REST's lower case. */
    @NonNull
    public static String dataType(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** The fields of GraphQL's {@link #FIELDS_QUERY} answer ("data"); empty when there are none. */
    @NonNull
    public static List<Field> fields(@Nullable JsonObject data) {
        List<Field> list = new ArrayList<>();
        JsonObject connection = data == null ? null : Json.obj(data, "repository.issueFields");
        if (connection == null || !connection.has("nodes") || !connection.get("nodes").isJsonArray()) return list;
        for (JsonElement element : connection.getAsJsonArray("nodes")) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject node = element.getAsJsonObject();
            long id = parseLong(Json.str(node, "fullDatabaseId"));
            String name = Json.str(node, "name");
            if (id == 0 || StringUtils.isBlank(name)) continue;
            List<Option> options = new ArrayList<>();
            if (node.has("options") && node.get("options").isJsonArray()) {
                for (JsonElement o : node.getAsJsonArray("options")) {
                    if (o == null || !o.isJsonObject()) continue;
                    String optionName = Json.str(o.getAsJsonObject(), "name");
                    if (StringUtils.isBlank(optionName)) continue;
                    options.add(new Option(optionName, Json.str(o.getAsJsonObject(), "color"),
                            Json.str(o.getAsJsonObject(), "description")));
                }
            }
            list.add(new Field(id, name, Json.str(node, "description"),
                    dataType(Json.str(node, "dataType")), options));
        }
        return list;
    }

    /** The enabled types of GET repos/o/r/issue-types. */
    @NonNull
    public static List<Type> types(@Nullable JsonArray array) {
        List<Type> list = new ArrayList<>();
        if (array == null) return list;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            if (o.has("is_enabled") && !Json.bool(o, "is_enabled")) continue;
            String name = Json.str(o, "name");
            if (StringUtils.isBlank(name)) continue;
            list.add(new Type(name, Json.str(o, "description"), Json.str(o, "color")));
        }
        return list;
    }

    /** The value the issue has for {@code fieldId}, or null. */
    @Nullable
    public static Issue.FieldValue valueOf(@Nullable List<Issue.FieldValue> values, long fieldId) {
        if (values == null) return null;
        for (Issue.FieldValue v : values) {
            if (v != null && v.fieldId == fieldId) return v;
        }
        return null;
    }

    /** True when the value holds something to show. */
    public static boolean hasValue(@Nullable Issue.FieldValue v) {
        return v != null && !display(v).isEmpty();
    }

    /** The text of a value: option names, a number without ".0", a date as "Nov 7, 2026". */
    @NonNull
    public static String display(@NonNull Issue.FieldValue v) {
        String type = dataType(v.dataType);
        if (SINGLE_SELECT.equals(type) && v.singleSelectOption != null
                && !StringUtils.isBlank(v.singleSelectOption.name)) {
            return v.singleSelectOption.name;
        }
        if (MULTI_SELECT.equals(type) && v.multiSelectOptions != null && !v.multiSelectOptions.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (Issue.FieldOption o : v.multiSelectOptions) {
                if (o == null || StringUtils.isBlank(o.name)) continue;
                if (text.length() > 0) text.append(", ");
                text.append(o.name);
            }
            return text.toString();
        }
        String raw = raw(v.value);
        if (raw.isEmpty()) return "";
        if (NUMBER.equals(type)) return number(raw);
        if (DATE.equals(type)) return date(raw);
        return raw;
    }

    /** The plain value: "High", "42.5", "2026-11-07" ("" for null). */
    @NonNull
    public static String raw(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString().trim();
        if (value.isJsonArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonElement e : value.getAsJsonArray()) {
                String part = raw(e);
                if (part.isEmpty()) continue;
                if (text.length() > 0) text.append(", ");
                text.append(part);
            }
            return text.toString();
        }
        return value.toString();
    }

    /** "42" for 42.0, "42.5" stays; anything that is not a number stays as it is. */
    @NonNull
    public static String number(@NonNull String raw) {
        try {
            double d = Double.parseDouble(raw.trim());
            if (Double.isInfinite(d) || Double.isNaN(d)) return raw;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
            String text = String.valueOf(d);
            return text.contains("E") ? new java.math.BigDecimal(raw.trim()).stripTrailingZeros().toPlainString() : text;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** "2026-11-07" (or a full ISO time) as "Nov 7, 2026"; other text stays. */
    @NonNull
    public static String date(@NonNull String raw) {
        Date d = parseDay(raw);
        if (d == null) return raw;
        SimpleDateFormat out = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        out.setTimeZone(TimeZone.getTimeZone("UTC"));
        return out.format(d);
    }

    /** The day of "2026-11-07..." at 00:00 UTC, or null. */
    @Nullable
    public static Date parseDay(@Nullable String raw) {
        if (raw == null || raw.trim().length() < 10) return null;
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        in.setTimeZone(TimeZone.getTimeZone("UTC"));
        in.setLenient(false);
        try {
            return in.parse(raw.trim().substring(0, 10));
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    /** "2026-11-07" for a picked day (month 0-11). */
    @NonNull
    public static String day(int year, int month, int dayOfMonth) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
    }

    // ---------------------------------------------------------------- writing

    /**
     * The value for POST issue-field-values: a number for number fields (null when the text is not
     * a number), the trimmed text for text and date fields (null when empty), the option name for
     * a single select.
     */
    @Nullable
    public static JsonElement value(@NonNull String dataType, @Nullable String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return null;
        if (NUMBER.equals(dataType(dataType))) {
            try {
                java.math.BigDecimal number = new java.math.BigDecimal(t.replace(',', '.'));
                number = number.stripTrailingZeros();
                if (number.scale() <= 0) {
                    try {
                        return new JsonPrimitive(number.longValueExact());
                    } catch (ArithmeticException e) {
                        return new JsonPrimitive(number.doubleValue());
                    }
                }
                return new JsonPrimitive(number.doubleValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new JsonPrimitive(t);
    }

    /** The option names of a multi select (null when none is picked). */
    @Nullable
    public static JsonElement value(@NonNull List<String> names) {
        JsonArray array = new JsonArray();
        for (String n : names) {
            if (!StringUtils.isBlank(n)) array.add(n);
        }
        return array.size() == 0 ? null : array;
    }

    /** {"issue_field_values":[{"field_id": id, "value": value}]} */
    @NonNull
    public static JsonObject body(long fieldId, @NonNull JsonElement value) {
        JsonObject item = new JsonObject();
        item.addProperty("field_id", fieldId);
        item.add("value", value);
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject body = new JsonObject();
        body.add("issue_field_values", array);
        return body;
    }

    /** The option names a value has picked (single or multi select). */
    @NonNull
    public static List<String> picked(@Nullable Issue.FieldValue v) {
        List<String> names = new ArrayList<>();
        if (v == null) return names;
        if (v.singleSelectOption != null && !StringUtils.isBlank(v.singleSelectOption.name)) {
            names.add(v.singleSelectOption.name);
        }
        if (v.multiSelectOptions != null) {
            for (Issue.FieldOption o : v.multiSelectOptions) {
                if (o != null && !StringUtils.isBlank(o.name)) names.add(o.name);
            }
        }
        return names;
    }

    private static long parseLong(@Nullable String text) {
        if (StringUtils.isBlank(text)) return 0;
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
