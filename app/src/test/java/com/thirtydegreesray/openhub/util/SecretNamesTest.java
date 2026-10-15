package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SecretNamesTest {

    @Test
    public void acceptsValidNames() {
        assertEquals(SecretNames.OK, SecretNames.check("TELEGRAM_TOKEN"));
        assertEquals(SecretNames.OK, SecretNames.check(" telegram_chat_id "));
        assertEquals(SecretNames.OK, SecretNames.check("_KEY2"));
        assertEquals("TELEGRAM_CHAT_ID", SecretNames.normalize(" telegram_chat_id "));
    }

    @Test
    public void refusesInvalidNames() {
        assertEquals(SecretNames.EMPTY, SecretNames.check(null));
        assertEquals(SecretNames.EMPTY, SecretNames.check("   "));
        assertEquals(SecretNames.BAD_CHARACTERS, SecretNames.check("MY-TOKEN"));
        assertEquals(SecretNames.BAD_CHARACTERS, SecretNames.check("MY TOKEN"));
        assertEquals(SecretNames.BAD_CHARACTERS, SecretNames.check("ТОКЕН"));
        assertEquals(SecretNames.STARTS_WITH_DIGIT, SecretNames.check("1TOKEN"));
        assertEquals(SecretNames.GITHUB_PREFIX, SecretNames.check("github_token"));
    }
}
