package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AttachmentsLocalCopiesTest {

    private static final long HOUR = 60L * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;
    private static final String PICTURE =
            "https://github.com/user-attachments/assets/2f53e9fa-73ea-47ee-b845-d8436a15904f";
    private static final String CLIP =
            "https://github.com/user-attachments/assets/a1f9bf6c-2872-485f-b2a8-1bc0fd068dc4";

    @Before
    public void setUp() {
        Attachments.clearLocalCopies();
    }

    @After
    public void tearDown() {
        Attachments.clearLocalCopies();
    }

    @Test
    public void survivesARestart() throws Exception {
        Attachments.putLocalCopy(PICTURE, "content://x/1", "image/png", NOW - HOUR);
        Attachments.putLocalCopy(CLIP, "content://x/2", "video/mp4", NOW);
        File dir = Files.createTempDirectory("att").toFile();
        File file = new File(dir, Attachments.STORE_NAME);
        Attachments.saveLocalCopies(file, NOW);
        assertTrue(file.isFile());
        assertFalse(new File(dir, Attachments.STORE_NAME + ".tmp").exists());

        Attachments.clearLocalCopies(); // the process died
        assertNull(Attachments.localCopy(PICTURE));
        Attachments.readLocalCopies(file, NOW + HOUR);
        assertEquals("content://x/1", Attachments.localCopy(PICTURE));
        assertEquals("image/png", Attachments.localType(PICTURE));
        assertFalse(Attachments.isVideoLink(PICTURE));
        assertEquals("content://x/2", Attachments.localCopy(CLIP));
        assertTrue(Attachments.isVideoLink(CLIP));
        assertEquals(2, Attachments.localUrls().size());
        file.delete();
        dir.delete();
    }

    @Test
    public void dropsLinksOlderThanADay() {
        Attachments.putLocalCopy(PICTURE, "content://x/1", "image/png", NOW - 25 * HOUR);
        Attachments.putLocalCopy(CLIP, "content://x/2", "video/mp4", NOW);
        assertNull("a new upload drops day-old links", Attachments.localCopy(PICTURE));
        Attachments.putLocalCopy(PICTURE, "content://x/1", "image/png", NOW - 23 * HOUR);
        Attachments.putLocalCopy(CLIP, "content://x/2", "video/mp4", NOW);
        String json = Attachments.toJson();
        Attachments.clearLocalCopies();
        Attachments.fromJson(json, NOW + 2 * HOUR);
        assertNull(Attachments.localCopy(PICTURE));
        assertNull(Attachments.localType(PICTURE));
        assertEquals("content://x/2", Attachments.localCopy(CLIP));
    }

    @Test
    public void keepsTheNewest200() {
        for (int i = 0; i < 250; i++) {
            Attachments.putLocalCopy(url(i), "content://x/" + i, "image/png", NOW - 250 + i);
        }
        assertEquals(Attachments.MAX_LOCAL_COPIES, Attachments.localUrls().size());
        assertNull(Attachments.localCopy(url(0)));
        assertNull(Attachments.localCopy(url(49)));
        assertEquals("content://x/50", Attachments.localCopy(url(50)));
        assertEquals("content://x/249", Attachments.localCopy(url(249)));
        String json = Attachments.toJson();
        Attachments.clearLocalCopies();
        Attachments.fromJson(json, NOW);
        assertEquals(Attachments.MAX_LOCAL_COPIES, Attachments.localUrls().size());
    }

    @Test
    public void ignoresBrokenFiles() throws Exception {
        Attachments.putLocalCopy(CLIP, "content://x/2", "video/mp4", NOW);
        Attachments.fromJson("not json {", NOW);
        Attachments.fromJson("{\"url\": 1}", NOW);
        Attachments.fromJson(null, NOW);
        Attachments.fromJson("[1, null, {\"url\": \"" + PICTURE + "\"},"
                + " {\"url\": \"" + PICTURE + "\", \"local\": \"content://x/1\", \"time\": \"soon\"},"
                + " {\"url\": \"" + PICTURE + "\", \"local\": \"content://x/1\"}]", NOW);
        assertNull(Attachments.localCopy(PICTURE));
        assertEquals("content://x/2", Attachments.localCopy(CLIP));
        File dir = Files.createTempDirectory("att").toFile();
        File file = new File(dir, Attachments.STORE_NAME);
        Files.write(file.toPath(), new byte[]{(byte) 0xff, 0, 1}, java.nio.file.StandardOpenOption.CREATE);
        Attachments.readLocalCopies(file, NOW);
        Attachments.readLocalCopies(new File(dir, "missing.json"), NOW);
        assertEquals(1, Attachments.localUrls().size());
        // a file from a phone with a wrong clock (far future) is not kept
        Attachments.fromJson("[{\"url\": \"" + PICTURE + "\", \"local\": \"content://x/1\", \"time\": "
                + (NOW + 48 * HOUR) + "}]", NOW);
        assertNull(Attachments.localCopy(PICTURE));
        // a link already known keeps its newer value
        Attachments.fromJson("[{\"url\": \"" + CLIP + "\", \"local\": \"content://old\", \"time\": "
                + NOW + "}]", NOW);
        assertEquals("content://x/2", Attachments.localCopy(CLIP));
        assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).length() == 3);
        file.delete();
        dir.delete();
    }

    private static String url(int i) {
        return String.format("https://github.com/user-attachments/assets/00000000-0000-0000-0000-%012d", i);
    }
}
