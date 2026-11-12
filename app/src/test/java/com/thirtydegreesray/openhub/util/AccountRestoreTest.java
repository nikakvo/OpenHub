package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountRestoreTest {

    @Test
    public void sameLoginWithAToken() {
        assertTrue(AccountRestore.usable("nikakvo", "gho_x", "nikakvo", false));
        assertTrue(AccountRestore.usable("nikakvo", "gho_x", "NikaKvo", false));
    }

    @Test
    public void notUsable() {
        assertFalse("no account", AccountRestore.usable(null, null, "nikakvo", false));
        assertFalse("token unreadable", AccountRestore.usable("nikakvo", "", "nikakvo", false));
        assertFalse("token unreadable", AccountRestore.usable("nikakvo", " ", "nikakvo", false));
        assertFalse("token unreadable", AccountRestore.usable("nikakvo", null, "nikakvo", false));
        assertFalse("refused by GitHub", AccountRestore.usable("nikakvo", "gho_x", "nikakvo", true));
        assertFalse("another account selected", AccountRestore.usable("other", "gho_x", "nikakvo", false));
        assertFalse("no saved profile", AccountRestore.usable("nikakvo", "gho_x", null, false));
    }
}
