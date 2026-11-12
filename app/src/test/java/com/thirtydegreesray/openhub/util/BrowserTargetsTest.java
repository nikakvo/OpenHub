package com.thirtydegreesray.openhub.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrowserTargetsTest {

    private static final String OURS = "io.github.nikakvo.openhub";
    private static final List<String> IGNORED = Arrays.asList("com.gh4a", "com.fastaccess", "com.taobao.taobao");

    private static BrowserTargets.Candidate c(String pkg, String activity) {
        return new BrowserTargets.Candidate(pkg, activity, true);
    }

    private static String names(List<BrowserTargets.Candidate> list) {
        StringBuilder out = new StringBuilder();
        for (BrowserTargets.Candidate x : list) out.append(out.length() == 0 ? "" : ",").append(x.packageName);
        return out.toString();
    }

    @Test
    public void browsersStayOursGoes() {
        List<BrowserTargets.Candidate> all = Arrays.asList(
                c(OURS, "com.thirtydegreesray.openhub.ui.activity.BrowserFilterActivity"),
                c("com.android.chrome", "com.google.android.apps.chrome.IntentDispatcher"),
                c("eu.weblibre.gecko", "eu.weblibre.flutter_mozilla_components.activities.IntentReceiverActivity"));
        assertEquals("com.android.chrome,eu.weblibre.gecko", names(BrowserTargets.pick(all, OURS, IGNORED)));
    }

    /** Issue #9: Android answers a github.com link with the GitHub apps only: nothing is left, the caller asks again for any web page. */
    @Test
    public void onlyGitHubClientsLeaveNothing() {
        List<BrowserTargets.Candidate> all = Arrays.asList(
                c(OURS, "BrowserFilterActivity"), c("com.gh4a", "com.gh4a.BrowseFilter"));
        assertTrue(BrowserTargets.pick(all, OURS, IGNORED).isEmpty());
        assertTrue(BrowserTargets.pick(Collections.singletonList(c(OURS, "x")), OURS, IGNORED).isEmpty());
        assertTrue(BrowserTargets.pick(Collections.<BrowserTargets.Candidate>emptyList(), OURS, IGNORED).isEmpty());
    }

    @Test
    public void notExportedIsSkipped() {
        List<BrowserTargets.Candidate> all = Arrays.asList(
                new BrowserTargets.Candidate("com.example.browser", "Internal", false),
                c("org.mozilla.firefox", "org.mozilla.fenix.IntentReceiverActivity"));
        assertEquals("org.mozilla.firefox", names(BrowserTargets.pick(all, OURS, IGNORED)));
    }

    @Test
    public void onePerAppKeepingTheFirst() {
        List<BrowserTargets.Candidate> all = Arrays.asList(
                c("org.mozilla.firefox", "org.mozilla.fenix.IntentReceiverActivity"),
                c("org.mozilla.firefox", "org.mozilla.fenix.IntentReceiverActivityAlias"),
                c("com.brave.browser", "com.google.android.apps.chrome.IntentDispatcher"));
        List<BrowserTargets.Candidate> out = BrowserTargets.pick(all, OURS, null);
        assertEquals("org.mozilla.firefox,com.brave.browser", names(out));
        assertEquals("org.mozilla.fenix.IntentReceiverActivity", out.get(0).activityName);
    }

    @Test
    public void directTarget() {
        assertTrue(BrowserTargets.isDirectTarget("eu.weblibre.gecko", "x.IntentReceiverActivity", OURS));
        assertFalse(BrowserTargets.isDirectTarget(OURS, "BrowserFilterActivity", OURS));
        assertFalse(BrowserTargets.isDirectTarget("android", "com.android.internal.app.ResolverActivity", OURS));
        assertFalse(BrowserTargets.isDirectTarget("com.android.intentresolver", "com.android.intentresolver.ResolverActivity", OURS));
        assertFalse(BrowserTargets.isDirectTarget("com.miui.x", "com.miui.x.ResolverActivity", OURS));
        assertFalse(BrowserTargets.isDirectTarget(null, null, OURS));
        assertFalse(BrowserTargets.isDirectTarget("", "a", OURS));
    }
}
