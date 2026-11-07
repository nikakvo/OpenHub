package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkflowStatesTest {

    @Test
    public void kinds() {
        assertEquals(WorkflowStates.Kind.ACTIVE, WorkflowStates.kind("active"));
        assertEquals(WorkflowStates.Kind.DISABLED, WorkflowStates.kind("disabled_manually"));
        assertEquals(WorkflowStates.Kind.DISABLED, WorkflowStates.kind("disabled_inactivity"));
        assertEquals(WorkflowStates.Kind.DISABLED, WorkflowStates.kind("disabled_fork"));
        assertEquals(WorkflowStates.Kind.DELETED, WorkflowStates.kind("deleted"));
        assertEquals(WorkflowStates.Kind.UNKNOWN, WorkflowStates.kind(null));
        assertEquals(WorkflowStates.Kind.UNKNOWN, WorkflowStates.kind("something_new"));
    }

    @Test
    public void enableAndDisable() {
        assertTrue(WorkflowStates.canDisable("active"));
        assertFalse(WorkflowStates.canEnable("active"));
        assertTrue(WorkflowStates.canEnable("disabled_manually"));
        assertTrue(WorkflowStates.canEnable("disabled_inactivity"));
        assertTrue(WorkflowStates.canEnable("disabled_fork"));
        assertFalse(WorkflowStates.canDisable("disabled_manually"));
        assertFalse(WorkflowStates.canEnable("deleted"));
        assertFalse(WorkflowStates.canDisable("deleted"));
        assertFalse(WorkflowStates.canEnable(null));
    }

    @Test
    public void links() {
        assertEquals("ci.yml", WorkflowStates.fileName(".github/workflows/ci.yml"));
        assertEquals("", WorkflowStates.fileName(null));
        assertEquals("https://github.com/o/r/actions/workflows/ci.yml",
                WorkflowStates.actionsUrl("o", "r", ".github/workflows/ci.yml"));
        assertEquals("https://github.com/o/r/actions/workflows/build%20kernel.yml",
                WorkflowStates.actionsUrl("o", "r", ".github/workflows/build kernel.yml"));
        assertEquals("https://github.com/o/r/actions",
                WorkflowStates.actionsUrl("o", "r", "dynamic/pages/pages-build-deployment"));
        assertEquals("https://github.com/o/r/actions", WorkflowStates.actionsUrl("o", "r", null));
    }
}
