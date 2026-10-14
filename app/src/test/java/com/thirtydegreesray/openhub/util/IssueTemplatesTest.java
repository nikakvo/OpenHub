package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class IssueTemplatesTest {

    @Test
    public void markdownTemplateWithFrontMatter() {
        String text = "---\nname: Bug report\nabout: Create a report\ntitle: \"[BUG] \"\n"
                + "labels: bug, needs triage\nassignees: ''\n\n---\n\n**Describe the bug**\nA clear description.\n";
        IssueTemplates.Template t = IssueTemplates.markdown(text);
        assertEquals("Bug report", t.name);
        assertEquals("Create a report", t.about);
        assertEquals("[BUG] ", t.title);
        assertEquals(Arrays.asList("bug", "needs triage"), t.labels);
        assertTrue(t.assignees.isEmpty());
        assertTrue(t.body.startsWith("**Describe the bug**"));
    }

    @Test
    public void markdownTemplateWithListsAndNoFrontMatter() {
        IssueTemplates.Template t = IssueTemplates.markdown(
                "---\nname: Idea\nlabels:\n  - enhancement\n  - \"good first issue\"\nassignees: [octocat]\n---\nBody");
        assertEquals(Arrays.asList("enhancement", "good first issue"), t.labels);
        assertEquals(Arrays.asList("octocat"), t.assignees);
        assertEquals("Body", t.body);

        IssueTemplates.Template plain = IssueTemplates.markdown("Just text\n");
        assertEquals("", plain.name);
        assertEquals("Just text\n", plain.body);
    }

    @Test
    public void issueFormBecomesSections() {
        String form = "name: Bug Report\ndescription: File a bug report.\ntitle: \"[Bug]: \"\n"
                + "labels: [\"bug\", \"triage\"]\nassignees:\n  - octocat\nbody:\n"
                + "  - type: markdown\n    attributes:\n      value: |\n        Thanks!\n"
                + "  - type: textarea\n    id: what\n    attributes:\n      label: What happened?\n      value: \"A bug happened!\"\n"
                + "  - type: dropdown\n    attributes:\n      label: Version\n      options:\n        - 1.0.2\n        - 1.0.3\n"
                + "  - type: checkboxes\n    attributes:\n      label: Code of Conduct\n      options:\n"
                + "        - label: I agree\n          required: true\n";
        IssueTemplates.Template t = IssueTemplates.form(form);
        assertEquals("Bug Report", t.name);
        assertEquals("File a bug report.", t.about);
        assertEquals("[Bug]: ", t.title);
        assertEquals(Arrays.asList("bug", "triage"), t.labels);
        assertEquals(Arrays.asList("octocat"), t.assignees);
        assertTrue(t.body.contains("### What happened?\n\nA bug happened!"));
        assertTrue(t.body.contains("### Version"));
        assertTrue(t.body.contains("1.0.2, 1.0.3"));
        assertTrue(t.body.contains("- [ ] I agree"));
        assertTrue("markdown blocks are not part of the issue", !t.body.contains("Thanks!"));
    }
}
