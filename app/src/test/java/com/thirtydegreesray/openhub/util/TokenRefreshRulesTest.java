package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.thirtydegreesray.openhub.util.TokenRefreshRules.Outcome;

import org.junit.Test;

public class TokenRefreshRulesTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void tokensThatDoNotExpireAreNeverRefreshed() {
        assertFalse(TokenRefreshRules.refreshSoon(null, NOW));
    }

    @Test
    public void refreshFiveMinutesBeforeTheEnd() {
        assertFalse(TokenRefreshRules.refreshSoon(NOW + 6 * 60_000L, NOW));
        assertTrue(TokenRefreshRules.refreshSoon(NOW + 5 * 60_000L, NOW));
        assertTrue(TokenRefreshRules.refreshSoon(NOW + 60_000L, NOW));
        // the test value 0 in the database: long expired
        assertTrue(TokenRefreshRules.refreshSoon(0L, NOW));
    }

    @Test
    public void newPairIsOk() {
        assertEquals(Outcome.OK, TokenRefreshRules.outcome(200, "gho_new", null));
        assertEquals(Outcome.OK, TokenRefreshRules.outcome(200, "gho_new", ""));
    }

    @Test
    public void onlyBadRefreshTokenMeansSignInAgain() {
        // GitHub sends it with 200 or 400
        assertEquals(Outcome.REFUSED, TokenRefreshRules.outcome(200, null, "bad_refresh_token"));
        assertEquals(Outcome.REFUSED, TokenRefreshRules.outcome(400, null, "bad_refresh_token"));
    }

    @Test
    public void everythingElseIsTemporary() {
        assertEquals(Outcome.TEMPORARY, TokenRefreshRules.outcome(-1, null, null)); // no network
        assertEquals(Outcome.TEMPORARY, TokenRefreshRules.outcome(502, null, null));
        assertEquals(Outcome.TEMPORARY, TokenRefreshRules.outcome(200, null, null));
        assertEquals(Outcome.TEMPORARY, TokenRefreshRules.outcome(200, " ", null));
        assertEquals(Outcome.TEMPORARY,
                TokenRefreshRules.outcome(200, null, "incorrect_client_credentials"));
        assertEquals(Outcome.TEMPORARY, TokenRefreshRules.outcome(200, "gho_x", "unauthorized_client"));
    }

    @Test
    public void pauseAfterATemporaryFailure() {
        assertTrue(TokenRefreshRules.mayTry(null, NOW));
        assertFalse(TokenRefreshRules.mayTry(NOW - 10_000L, NOW));
        assertTrue(TokenRefreshRules.mayTry(NOW - 30_000L, NOW));
        assertTrue(TokenRefreshRules.mayTry(NOW + 60_000L, NOW)); // clock set back
    }

    @Test
    public void tokenOfHeader() {
        assertEquals("gho_a", TokenRefreshRules.tokenOf("token gho_a"));
        assertEquals("gho_a", TokenRefreshRules.tokenOf("Bearer gho_a"));
        assertEquals("gho_a", TokenRefreshRules.tokenOf("TOKEN  gho_a "));
        assertNull(TokenRefreshRules.tokenOf("Basic abc"));
        assertNull(TokenRefreshRules.tokenOf("token "));
        assertNull(TokenRefreshRules.tokenOf(null));
    }

    @Test
    public void replayOnlySmallRepeatableBodies() {
        assertTrue(TokenRefreshRules.canReplay(false, false, -1));
        assertTrue(TokenRefreshRules.canReplay(true, false, 120));
        assertFalse(TokenRefreshRules.canReplay(true, true, 120));
        assertFalse(TokenRefreshRules.canReplay(true, false, -1));
        assertFalse(TokenRefreshRules.canReplay(true, false, 5 * 1024 * 1024L));
    }
}
