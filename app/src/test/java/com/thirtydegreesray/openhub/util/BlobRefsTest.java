package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class BlobRefsTest {

    @Test
    public void segments() {
        assertEquals(Arrays.asList("feature", "x", "docs", "a.md"),
                BlobRefs.segments("feature", "x/docs/a.md"));
        assertEquals(Arrays.asList("main", "README.md"), BlobRefs.segments("main", "README.md"));
        assertEquals(Collections.singletonList("a"), BlobRefs.segments(null, "a"));
    }

    @Test
    public void longestRef() {
        List<String> s = BlobRefs.segments("feature", "x/docs/a.md");
        assertEquals(2, BlobRefs.longestRef(s, Arrays.asList("feature", "feature/x", "main")));
        assertEquals(1, BlobRefs.longestRef(s, Arrays.asList("feature", "main")));
        assertEquals(0, BlobRefs.longestRef(s, Collections.singletonList("main")));
        assertEquals(0, BlobRefs.longestRef(s, Collections.emptyList()));
        // the whole link is never a ref: a file path must stay
        List<String> two = BlobRefs.segments("a", "b");
        assertEquals(1, BlobRefs.longestRef(two, Arrays.asList("a", "a/b")));
        // "master" of square/okhttp: only "main" exists
        assertEquals(0, BlobRefs.longestRef(BlobRefs.segments("master", "README.md"),
                Arrays.asList("main", "master-old")));
    }

    @Test
    public void names() {
        assertEquals("feature/x", BlobRefs.shortName("refs/heads/feature/x"));
        assertEquals("v1.0", BlobRefs.shortName("refs/tags/v1.0"));
        assertEquals(Arrays.asList("a", "b/c"),
                BlobRefs.shortNames(Arrays.asList("refs/heads/a", "refs/tags/b/c")));
        assertEquals("feature/x/docs", BlobRefs.join(BlobRefs.segments("feature", "x/docs/a.md"), 0, 3));
    }

    @Test
    public void sha() {
        assertTrue(BlobRefs.looksLikeSha("04609c1"));
        assertTrue(BlobRefs.looksLikeSha("04609C1AB04609C1AB04609C1AB04609C1AB0460"));
        assertFalse(BlobRefs.looksLikeSha("main"));
        assertFalse(BlobRefs.looksLikeSha("abc12"));
        assertFalse(BlobRefs.looksLikeSha("decade-branch"));
        assertFalse(BlobRefs.looksLikeSha(null));
    }
}
