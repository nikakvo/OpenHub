package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OAuthSessionTest {

    /** The example of RFC 7636, appendix B. */
    @Test
    public void pkceChallengeMatchesTheRfc() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                OAuthSession.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    public void randomTokensAreLongAndUrlSafe() {
        String a = OAuthSession.randomToken(48);
        String b = OAuthSession.randomToken(48);
        assertNotEquals(a, b);
        assertEquals(64, a.length()); // 48 bytes in Base64 without padding
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }
}
