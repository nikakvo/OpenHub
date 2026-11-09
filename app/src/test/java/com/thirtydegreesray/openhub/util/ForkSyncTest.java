package com.thirtydegreesray.openhub.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ForkSyncTest {

    @Test
    public void defaultBranchFollowsTheParentsDefaultBranch() {
        assertEquals("main", ForkSync.upstreamBranch("master", "master", "main"));
        assertEquals("main", ForkSync.upstreamBranch("main", "main", "main"));
    }

    @Test
    public void otherBranchesUseTheSameName() {
        assertEquals("dev", ForkSync.upstreamBranch("dev", "main", "main"));
        assertEquals("main", ForkSync.upstreamBranch("main", "main", null));
        assertEquals("main", ForkSync.upstreamBranch("", "main", "main"));
    }

    @Test
    public void states() {
        assertEquals(ForkSync.State.UP_TO_DATE, ForkSync.state(0, 0));
        assertEquals(ForkSync.State.AHEAD, ForkSync.state(4, 0));
        assertEquals(ForkSync.State.BEHIND, ForkSync.state(0, 8));
        assertEquals(ForkSync.State.DIVERGED, ForkSync.state(4, 8));
    }

    @Test
    public void baseheadAndLabel() {
        assertEquals("PerformanC:main...main", ForkSync.basehead("PerformanC", "main", "main"));
        assertEquals("o:feature/a%20b...feature/a%20b", ForkSync.basehead("o", "feature/a b", "feature/a b"));
        assertEquals("PerformanC/ReZygisk:main", ForkSync.label("PerformanC/ReZygisk", "main"));
        assertEquals("PerformanC", ForkSync.ownerOf("PerformanC/ReZygisk"));
    }

    @Test
    public void results() {
        assertEquals(ForkSync.Result.FAST_FORWARD, ForkSync.result("fast-forward"));
        assertEquals(ForkSync.Result.MERGE, ForkSync.result("merge"));
        assertEquals(ForkSync.Result.NONE, ForkSync.result("none"));
        assertEquals(ForkSync.Result.UNKNOWN, ForkSync.result(null));
    }

    @Test
    public void conflictUrl() {
        assertEquals("https://github.com/nikakvo/ReZygisk/compare/main...PerformanC:ReZygisk:main",
                ForkSync.conflictUrl("nikakvo/ReZygisk", "main", "PerformanC/ReZygisk", "main"));
    }
}
