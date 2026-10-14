package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ActionsLinksTest {

    @Test
    public void runAndJobLinks() {
        ActionsLinks run = ActionsLinks.parse("https://github.com/nikakvo/test/actions/runs/123456789");
        assertEquals("nikakvo", run.owner);
        assertEquals("test", run.repo);
        assertEquals(123456789L, run.runId);
        assertEquals(0, run.jobId);

        ActionsLinks job = ActionsLinks.parse("https://github.com/a/b/actions/runs/5/job/77?pr=1");
        assertEquals(5, job.runId);
        assertEquals(77, job.jobId);

        ActionsLinks attempt = ActionsLinks.parse("http://www.github.com/a/b/actions/runs/5/attempts/2/");
        assertEquals(5, attempt.runId);
    }

    @Test
    public void otherLinks() {
        assertNull(ActionsLinks.parse(null));
        assertNull(ActionsLinks.parse("https://github.com/a/b/actions"));
        assertNull(ActionsLinks.parse("https://github.com/a/b/actions/workflows/ci.yml"));
        assertNull(ActionsLinks.parse("https://github.com.evil.example/a/b/actions/runs/5"));
        assertNull(ActionsLinks.parse("https://github.com/a/b/pull/5"));
    }
}
