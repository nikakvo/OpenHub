package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class SealedBoxTest {

    /** Secret key 01 02 .. 20 (hex); public key and box made with libsodium (PyNaCl). */
    private static final byte[] SECRET_KEY = new byte[32];
    private static final byte[] PUBLIC_KEY =
            Base64.getDecoder().decode("B6N8vBQgk8i3VdwbEOhstCY3StFqqFPtC9/AsrhtHHw=");
    private static final byte[] LIBSODIUM_BOX = Base64.getDecoder().decode(
            "PvMZHLpXnOiuWcng1KBBZqT1bfX0w6F1vJNC0ZIxOn8Z3Ub1z3rW8LYZr1dvzxBFGLqijiEbSYsZ/HF70sB2CXuH3tCS4qiT");

    static {
        for (int i = 0; i < 32; i++) SECRET_KEY[i] = (byte) (i + 1);
    }

    @Test
    public void opensBoxMadeByLibsodium() {
        byte[] message = SealedBox.open(LIBSODIUM_BOX, PUBLIC_KEY, SECRET_KEY);
        assertArrayEquals("ghp_example-secret-value".getBytes(StandardCharsets.UTF_8), message);
    }

    @Test
    public void sealsSoThatTheKeyOwnerCanOpen() {
        byte[] message = "line 1\nline 2 ü€".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = SealedBox.seal(message, PUBLIC_KEY);
        assertEquals(48 + message.length, sealed.length);
        assertArrayEquals(message, SealedBox.open(sealed, PUBLIC_KEY, SECRET_KEY));
    }

    @Test
    public void emptyAndLargeValues() {
        byte[][] pair = SealedBox.keyPair(new SecureRandom());
        byte[] large = new byte[SecretNames.MAX_VALUE_BYTES];
        new SecureRandom().nextBytes(large);
        for (byte[] message : new byte[][]{new byte[0], large}) {
            assertArrayEquals(message, SealedBox.open(SealedBox.seal(message, pair[0]), pair[0], pair[1]));
        }
    }

    @Test
    public void tamperedOrForeignBoxIsRefused() {
        byte[] sealed = SealedBox.seal("value".getBytes(StandardCharsets.UTF_8), PUBLIC_KEY);
        sealed[sealed.length - 1] ^= 1;
        assertNull(SealedBox.open(sealed, PUBLIC_KEY, SECRET_KEY));

        byte[][] other = SealedBox.keyPair(new SecureRandom());
        byte[] foreign = SealedBox.seal("value".getBytes(StandardCharsets.UTF_8), other[0]);
        assertNull(SealedBox.open(foreign, PUBLIC_KEY, SECRET_KEY));
    }
}
