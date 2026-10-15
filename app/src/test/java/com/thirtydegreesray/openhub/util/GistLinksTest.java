package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GistLinksTest {

    @Test
    public void gistLinks() {
        assertEquals("aa5a315d61ae9438b18d", GistLinks.id("https://gist.github.com/nikakvo/aa5a315d61ae9438b18d"));
        assertEquals("aa5a315d61ae9438b18d", GistLinks.id("https://gist.github.com/aa5a315d61ae9438b18d"));
        assertEquals("aa5a315d61ae9438b18d", GistLinks.id("https://gist.github.com/x/AA5A315D61AE9438B18D#file-a-sh"));
        assertEquals("abc12", GistLinks.id("https://gist.github.com/x/abc12/0123456789012345678901234567890123456789"));
        assertNull(GistLinks.id("https://gist.github.com/nikakvo"));
        assertNull(GistLinks.id("https://github.com/nikakvo/aa5a315d61ae9438b18d"));
        assertNull(GistLinks.id("https://gist.github.com.evil.example/x/abcdef"));
        assertNull(GistLinks.id(null));
    }
}
