package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PullLinksTest {

    @Test
    public void tabOfALink() {
        assertEquals(PullLinks.FILES, PullLinks.tab("https://github.com/o/r/pull/12/files"));
        assertEquals(PullLinks.FILES, PullLinks.tab("https://github.com/o/r/pull/12/files#diff-abc"));
        assertEquals(PullLinks.FILES, PullLinks.tab("https://github.com/o/r/pull/12/changes"));
        assertEquals(PullLinks.COMMITS, PullLinks.tab("https://github.com/o/r/pull/12/commits"));
        assertEquals(PullLinks.COMMITS, PullLinks.tab("https://github.com/o/r/pull/12/commits/abc123"));
        assertEquals(PullLinks.CONVERSATION, PullLinks.tab("https://github.com/o/r/pull/12"));
        assertEquals(PullLinks.CONVERSATION, PullLinks.tab("https://github.com/o/r/pull/12#issuecomment-1"));
        assertEquals(PullLinks.CONVERSATION, PullLinks.tab("https://github.com/o/r/pull/12/checks"));
        assertEquals(PullLinks.CONVERSATION, PullLinks.tab("https://api.github.com/repos/o/r/pulls/12"));
    }
}
