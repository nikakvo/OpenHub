package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GitHubLinkOwnerTest {

    private static final String SELF = "io.github.nikakvo.openhub";
    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.brave.browser", "org.mozilla.firefox"));

    @Test
    public void whoOpensGitHubLinks() {
        assertEquals(GitHubLinkOwner.Owner.OPENHUB, GitHubLinkOwner.decide(SELF, SELF, BROWSERS));
        assertEquals(GitHubLinkOwner.Owner.OTHER_APP,
                GitHubLinkOwner.decide("com.github.android", SELF, BROWSERS));
        assertEquals(GitHubLinkOwner.Owner.BROWSER,
                GitHubLinkOwner.decide("com.android.chrome", SELF, BROWSERS));
        // Android asks every time (the chooser), or nothing found
        assertEquals(GitHubLinkOwner.Owner.BROWSER,
                GitHubLinkOwner.decide("com.android.intentresolver", SELF, BROWSERS));
        assertEquals(GitHubLinkOwner.Owner.BROWSER, GitHubLinkOwner.decide("android", SELF, BROWSERS));
        assertEquals(GitHubLinkOwner.Owner.BROWSER, GitHubLinkOwner.decide(null, SELF, BROWSERS));
        // no browser list: a real other app still counts as OTHER_APP
        assertEquals(GitHubLinkOwner.Owner.OTHER_APP,
                GitHubLinkOwner.decide("com.github.android", SELF, new HashSet<>()));
    }
}
