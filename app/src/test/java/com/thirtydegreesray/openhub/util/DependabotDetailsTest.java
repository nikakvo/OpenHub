package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class DependabotDetailsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static final JsonObject FULL = json("{"
            + "\"dependency\":{\"version_requirement\":\"< 2.31.0\",\"relationship\":\"direct\",\"scope\":\"runtime\"},"
            + "\"security_advisory\":{\"cvss_severities\":{"
            + "\"cvss_v3\":{\"vector_string\":\"CVSS:3.1/AV:N/AC:L\",\"score\":7.5},"
            + "\"cvss_v4\":{\"vector_string\":\"CVSS:4.0/AV:N\",\"score\":8.7}},"
            + "\"epss\":{\"percentage\":0.00045,\"percentile\":0.1234}},"
            + "\"reachability\":{\"verdict\":\"reachable\",\"reason\":\"unknown\"}}");

    @Test
    public void everythingGitHubSent() {
        assertEquals("< 2.31.0", DependabotDetails.versionRequirement(FULL));
        assertEquals("8.7", DependabotDetails.cvss(FULL, "cvss_v4"));
        assertEquals("7.5", DependabotDetails.cvss(FULL, "cvss_v3"));
        assertEquals("CVSS:4.0/AV:N", DependabotDetails.cvssVector(FULL, "cvss_v4"));
        assertArrayEquals(new String[]{"0.05", "12"}, DependabotDetails.epss(FULL));
        assertEquals("reachable", DependabotDetails.reachability(FULL));
        assertEquals("direct", DependabotDetails.relationship(FULL));
        assertEquals("runtime", DependabotDetails.scope(FULL));
    }

    @Test
    public void missingOrEmptyValuesShowNothing() {
        JsonObject alert = json("{\"dependency\":{\"version_requirement\":null,\"relationship\":\"unknown\"},"
                + "\"security_advisory\":{\"cvss_severities\":{\"cvss_v3\":{\"vector_string\":null,\"score\":0},"
                + "\"cvss_v4\":null},\"epss\":null},\"reachability\":{\"verdict\":\"unavailable\"}}");
        assertNull(DependabotDetails.versionRequirement(alert));
        assertNull(DependabotDetails.cvss(alert, "cvss_v3"));
        assertNull(DependabotDetails.cvss(alert, "cvss_v4"));
        assertEquals("", DependabotDetails.cvssVector(alert, "cvss_v3"));
        assertNull(DependabotDetails.epss(alert));
        assertNull(DependabotDetails.reachability(alert));
        assertNull(DependabotDetails.relationship(alert));
        assertNull(DependabotDetails.scope(alert));
        assertNull(DependabotDetails.versionRequirement(null));
    }

    @Test
    public void emptyRequirementMeansNoConstraint() {
        assertEquals("", DependabotDetails.versionRequirement(json("{\"dependency\":{\"version_requirement\":\"\"}}")));
    }

    @Test
    public void bigEpssGetsOneDecimal() {
        assertArrayEquals(new String[]{"94.3", null},
                DependabotDetails.epss(json("{\"security_advisory\":{\"epss\":{\"percentage\":0.9431}}}")));
    }
}
