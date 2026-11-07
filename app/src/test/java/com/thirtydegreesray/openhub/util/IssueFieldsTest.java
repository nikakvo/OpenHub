package com.thirtydegreesray.openhub.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.mvp.model.Issue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IssueFieldsTest {

    private static Issue.FieldValue value(String json) {
        return new Gson().fromJson(json, Issue.FieldValue.class);
    }

    @Test
    public void displaysEveryDataType() {
        assertEquals("Sample text", IssueFields.display(value(
                "{\"issue_field_id\":1,\"data_type\":\"text\",\"value\":\"Sample text\"}")));
        assertEquals("42", IssueFields.display(value(
                "{\"issue_field_id\":2,\"data_type\":\"number\",\"value\":42.0}")));
        assertEquals("42.5", IssueFields.display(value(
                "{\"issue_field_id\":2,\"data_type\":\"number\",\"value\":42.5}")));
        assertEquals("Nov 7, 2026", IssueFields.display(value(
                "{\"issue_field_id\":3,\"data_type\":\"date\",\"value\":\"2026-11-07\"}")));
        assertEquals("High", IssueFields.display(value(
                "{\"issue_field_id\":4,\"data_type\":\"single_select\",\"value\":\"High\","
                        + "\"single_select_option\":{\"id\":9,\"name\":\"High\",\"color\":\"red\"}}")));
        assertEquals("iOS, Android", IssueFields.display(value(
                "{\"issue_field_id\":5,\"data_type\":\"multi_select\",\"value\":null,"
                        + "\"multi_select_options\":[{\"id\":1,\"name\":\"iOS\",\"color\":\"blue\"},"
                        + "{\"id\":2,\"name\":\"Android\",\"color\":\"green\"}]}")));
        assertFalse(IssueFields.hasValue(value("{\"issue_field_id\":1,\"data_type\":\"text\",\"value\":null}")));
    }

    @Test
    public void readsFieldsFromGraphQl() {
        JsonObject data = JsonParser.parseString("{\"repository\":{\"issueFields\":{\"nodes\":["
                + "{\"fullDatabaseId\":\"123\",\"name\":\"Priority\",\"description\":null,\"dataType\":\"SINGLE_SELECT\","
                + "\"options\":[{\"name\":\"High\",\"color\":\"RED\",\"description\":\"\"},{\"name\":\"Low\",\"color\":\"GRAY\"}]},"
                + "{\"fullDatabaseId\":\"124\",\"name\":\"Start\",\"dataType\":\"DATE\"},"
                + "{},"
                + "{\"fullDatabaseId\":null,\"name\":\"Broken\",\"dataType\":\"TEXT\"}]}}}").getAsJsonObject();
        List<IssueFields.Field> fields = IssueFields.fields(data);
        assertEquals(2, fields.size());
        assertEquals(123L, fields.get(0).id);
        assertEquals(IssueFields.SINGLE_SELECT, fields.get(0).dataType);
        assertEquals(2, fields.get(0).options.size());
        assertEquals(IssueFields.DATE, fields.get(1).dataType);
        assertTrue(IssueFields.fields(JsonParser.parseString("{\"repository\":{\"issueFields\":null}}")
                .getAsJsonObject()).isEmpty());
        assertTrue(IssueFields.fields(new JsonObject()).isEmpty());
    }

    @Test
    public void readsEnabledTypes() {
        JsonArray array = JsonParser.parseString("[{\"id\":1,\"name\":\"Bug\",\"color\":\"red\",\"is_enabled\":true},"
                + "{\"id\":2,\"name\":\"Old\",\"is_enabled\":false},{\"id\":3,\"name\":\"Task\"}]").getAsJsonArray();
        List<IssueFields.Type> types = IssueFields.types(array);
        assertEquals(2, types.size());
        assertEquals("Bug", types.get(0).name);
        assertEquals("Task", types.get(1).name);
    }

    @Test
    public void buildsValuesToSend() {
        assertEquals("42", IssueFields.value("number", "42").toString());
        assertEquals("42.5", IssueFields.value("number", "42,5").toString());
        assertEquals("-3", IssueFields.value("number", " -3.00 ").toString());
        assertNull(IssueFields.value("number", "abc"));
        assertNull(IssueFields.value("text", "   "));
        assertEquals("\"Hello\"", IssueFields.value("text", " Hello ").toString());
        assertEquals("[\"iOS\",\"Android\"]", IssueFields.value(Arrays.asList("iOS", "", "Android")).toString());
        assertNull(IssueFields.value(Collections.<String>emptyList()));
        JsonElement body = IssueFields.body(7L, IssueFields.value("text", "x"));
        assertEquals("{\"issue_field_values\":[{\"field_id\":7,\"value\":\"x\"}]}", body.toString());
        assertEquals("2026-01-05", IssueFields.day(2026, 0, 5));
    }

    @Test
    public void colorsAndNumbers() {
        assertEquals(0xFFF85149, IssueFields.color("RED"));
        assertEquals(0xFF8B949E, IssueFields.color(null));
        assertEquals("1000000", IssueFields.number("1000000.0"));
        assertEquals("text", IssueFields.number("text"));
        assertEquals("2026-13-45", IssueFields.date("2026-13-45"));
    }

    @Test
    public void picksOptionNamesAndFindsValues() {
        Issue.FieldValue multi = value("{\"issue_field_id\":5,\"data_type\":\"multi_select\","
                + "\"multi_select_options\":[{\"id\":1,\"name\":\"iOS\"},{\"id\":2,\"name\":\"Android\"}]}");
        assertEquals(Arrays.asList("iOS", "Android"), IssueFields.picked(multi));
        assertEquals(multi, IssueFields.valueOf(Collections.singletonList(multi), 5));
        assertNull(IssueFields.valueOf(Collections.singletonList(multi), 6));
        assertNull(IssueFields.valueOf(null, 5));
    }
}
