package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The decisions of a commit, without GitHub. */
public class RepoCommitterTest {

    @Test
    public void commitToTheCurrentBranch() {
        RepoCommitter.Options o = new RepoCommitter.Options("Update README.md", null, null, false);
        assertEquals("main", RepoCommitter.targetBranch("main", o));
        assertNull(RepoCommitter.pullHeadAndBase("main", o));
    }

    @Test
    public void newBranchWithPullRequestGoesIntoTheCurrentBranch() {
        RepoCommitter.Options o = new RepoCommitter.Options("Fix", null, "fix-1", true);
        assertEquals("fix-1", RepoCommitter.targetBranch("main", o));
        assertArrayEquals(new String[]{"fix-1", "main"}, RepoCommitter.pullHeadAndBase("main", o));
    }

    @Test
    public void pullRequestFromTheCurrentBranchNeedsABase() {
        RepoCommitter.Options withBase = new RepoCommitter.Options("Fix", null, null, true, "main");
        assertArrayEquals(new String[]{"dev", "main"}, RepoCommitter.pullHeadAndBase("dev", withBase));

        // no base and no new branch: there is nothing to open a pull request into
        RepoCommitter.Options noBase = new RepoCommitter.Options("Fix", null, null, true, null);
        assertFalse(noBase.openPullRequest);
        assertNull(RepoCommitter.pullHeadAndBase("dev", noBase));

        // a base is ignored when a new branch is created (the PR goes into the current one)
        RepoCommitter.Options both = new RepoCommitter.Options("Fix", null, "fix-2", true, "release");
        assertNull(both.pullBase);
        assertArrayEquals(new String[]{"fix-2", "dev"}, RepoCommitter.pullHeadAndBase("dev", both));
    }

    @Test
    public void commitMessage() {
        assertEquals("Title", new RepoCommitter.Options("Title", "  ", null, false).fullMessage());
        assertEquals("Title\n\nMore text",
                new RepoCommitter.Options("Title", "  More text \n", null, false).fullMessage());
    }

    @Test
    public void fileModes() {
        assertEquals("100755", RepoCommitter.keptMode("100755"));
        assertEquals("100644", RepoCommitter.keptMode("100644"));
        assertEquals("100644", RepoCommitter.keptMode(null));      // new file
        assertEquals("100644", RepoCommitter.keptMode("120000"));  // symlink
        assertEquals("100644", RepoCommitter.keptMode("160000"));  // submodule
        assertEquals("100644", RepoCommitter.keptMode("040000")); // a folder entry
    }

    @Test
    public void paths() {
        assertArrayEquals(new String[]{"", "README.md"}, RepoCommitter.splitPath("README.md"));
        assertArrayEquals(new String[]{"docs/api", "index.md"}, RepoCommitter.splitPath("docs/api/index.md"));
    }

    @Test
    public void folders() {
        assertEquals("", RepoCommitter.cleanFolder(null));
        assertEquals("", RepoCommitter.cleanFolder("  "));
        assertEquals("", RepoCommitter.cleanFolder("/"));
        assertEquals("docs/images", RepoCommitter.cleanFolder(" /docs//images/ "));
        assertEquals("docs/images", RepoCommitter.cleanFolder("docs\\images"));
        assertNull(RepoCommitter.cleanFolder("docs/../secret"));
        assertNull(RepoCommitter.cleanFolder("./docs"));
        assertEquals("a.b/c..d", RepoCommitter.cleanFolder("a.b/c..d"));
    }
}
