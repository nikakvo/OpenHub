package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActionsScopesTest {

    @Test
    public void repositoryScope() {
        String base = ActionsScopes.base("o", "r", ActionsScopes.ACTIONS);
        assertEquals("repos/o/r/actions", base);
        assertEquals("repos/o/r/actions/secrets", ActionsScopes.secrets(base));
        assertEquals("repos/o/r/actions/secrets/public-key", ActionsScopes.publicKey(base));
        assertEquals("repos/o/r/actions/secrets/TOKEN", ActionsScopes.secret(base, "TOKEN"));
        assertEquals("repos/o/r/actions/variables", ActionsScopes.variables(base));
        assertEquals("repos/o/r/actions/variables/MODE", ActionsScopes.variable(base, "MODE"));
        assertEquals("https://github.com/o/r/settings/secrets/actions",
                ActionsScopes.settingsPage("o", "r", ActionsScopes.ACTIONS, false));
        assertEquals("https://github.com/o/r/settings/variables/actions",
                ActionsScopes.settingsPage("o", "r", ActionsScopes.ACTIONS, true));
        assertEquals("repos/o/r/actions", ActionsScopes.base("o", "r", null));
    }

    @Test
    public void dependabotScope() {
        String base = ActionsScopes.base("o", "r", ActionsScopes.DEPENDABOT);
        assertEquals("repos/o/r/dependabot/secrets/public-key", ActionsScopes.publicKey(base));
        assertEquals("https://github.com/o/r/settings/secrets/dependabot",
                ActionsScopes.settingsPage("o", "r", ActionsScopes.DEPENDABOT, false));
    }

    @Test
    public void environmentScope() {
        String scope = ActionsScopes.environment("Prod / EU");
        assertTrue(ActionsScopes.isEnvironment(scope));
        assertEquals("Prod / EU", ActionsScopes.environmentName(scope));
        String base = ActionsScopes.base("o", "r", scope);
        assertEquals("repos/o/r/environments/Prod%20%2F%20EU", base);
        assertEquals("repos/o/r/environments/Prod%20%2F%20EU/secrets/KEY", ActionsScopes.secret(base, "KEY"));
        assertEquals("https://github.com/o/r/settings/environments",
                ActionsScopes.settingsPage("o", "r", scope, true));
        assertFalse(ActionsScopes.isEnvironment("env:"));
        assertNull(ActionsScopes.environmentName(ActionsScopes.ACTIONS));
        assertEquals("repos/o/r/environments", ActionsScopes.environmentsUrl("o", "r"));
    }
}
