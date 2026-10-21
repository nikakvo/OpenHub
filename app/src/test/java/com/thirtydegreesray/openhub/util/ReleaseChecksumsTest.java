package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReleaseChecksumsTest {

    private static final String A = "a".repeat(64);
    private static final String B = "0123456789abcdef".repeat(4);

    @Test
    public void sortedSha256sumFormatWithoutItself() {
        Map<String, String> digests = new LinkedHashMap<>();
        digests.put("zeta.zip", "sha256:" + A);
        digests.put("SHA256SUMS", "sha256:" + B);
        digests.put("app.apk", "SHA256:" + B.toUpperCase());
        ReleaseChecksums.Result result = ReleaseChecksums.build(digests);
        assertEquals(B + "  app.apk\n" + A + "  zeta.zip\n", result.text);
        assertEquals(2, result.count);
        assertEquals(0, result.missing.size());
    }

    @Test
    public void filesWithoutDigestAreListedAsMissing() {
        Map<String, String> digests = new LinkedHashMap<>();
        digests.put("old.zip", null);
        digests.put("new.zip", "sha256:" + A);
        digests.put("bad.zip", "md5:abc");
        ReleaseChecksums.Result result = ReleaseChecksums.build(digests);
        assertEquals(A + "  new.zip\n", result.text);
        assertEquals(Arrays.asList("bad.zip", "old.zip"), result.missing);
    }

    @Test
    public void emptyWhenNothingHasADigest() {
        Map<String, String> digests = new LinkedHashMap<>();
        digests.put("old.zip", "");
        assertEquals("", ReleaseChecksums.build(digests).text);
        assertNull(ReleaseChecksums.hex("sha256:xyz"));
    }
}
