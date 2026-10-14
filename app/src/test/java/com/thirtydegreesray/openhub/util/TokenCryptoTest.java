package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * TokenCrypto without the Android Keystore (as on a phone where it does not work): the
 * app must keep working with plain tokens and must never "open" a sealed token wrongly.
 */
public class TokenCryptoTest {

    @Test
    public void withoutKeystoreTokensStayPlain() {
        assertEquals("gho_abc123", TokenCrypto.seal("gho_abc123"));
        assertEquals("gho_abc123", TokenCrypto.open("gho_abc123"));
    }

    @Test
    public void emptyAndSealedValuesAreNotSealedAgain() {
        assertNull(TokenCrypto.seal(null));
        assertEquals("", TokenCrypto.seal(""));
        assertEquals("v1:AAAA", TokenCrypto.seal("v1:AAAA"));
    }

    @Test
    public void aSealedTokenThatCannotBeOpenedGivesEmpty() {
        // keys gone (or no Keystore): "" means signed out, never the stored text
        assertEquals("", TokenCrypto.open("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertNull(TokenCrypto.open(null));
    }

    @Test
    public void sealedForm() {
        assertTrue(TokenCrypto.isSealed("v1:abc"));
        assertFalse(TokenCrypto.isSealed("ghp_abc"));
        assertFalse(TokenCrypto.isSealed("V1:abc"));
        assertFalse(TokenCrypto.isSealed(null));
    }
}
