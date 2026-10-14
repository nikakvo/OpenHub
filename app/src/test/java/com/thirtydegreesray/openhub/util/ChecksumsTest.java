package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class ChecksumsTest {

    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void sha256OfAStream() throws Exception {
        // FIPS 180-2 test vectors
        assertEquals(ABC, Checksums.sha256(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII))));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Checksums.sha256(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void githubDigests() {
        assertEquals(ABC, Checksums.sha256Of("sha256:" + ABC));
        assertEquals(ABC, Checksums.sha256Of("SHA256:" + ABC.toUpperCase()));
        assertEquals(ABC, Checksums.sha256Of("  " + ABC + " "));
        assertNull(Checksums.sha256Of(null));
        assertNull(Checksums.sha256Of(""));
        assertNull(Checksums.sha256Of("sha1:a9993e364706816aba3e25717850c26c9cd0d89d"));
        assertNull(Checksums.sha256Of("sha256:" + ABC.substring(1)));
        assertNull(Checksums.sha256Of("sha256:" + ABC.replace('a', 'g')));
    }

    @Test
    public void shortForm() {
        assertEquals("ba7816bf8f01", Checksums.shortForm(ABC));
    }
}
