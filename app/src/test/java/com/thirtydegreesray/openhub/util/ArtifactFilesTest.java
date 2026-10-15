package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArtifactFilesTest {

    private static byte[] zip(String... namesAndTexts) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndTexts.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndTexts[i]));
                if (!namesAndTexts[i].endsWith("/")) {
                    out.write(namesAndTexts[i + 1].getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    public void unzipFlattensAndStaysInside() throws Exception {
        File dir = Files.createTempDirectory("artifact").toFile();
        File outside = new File(dir.getParentFile(), "evil.txt");
        try {
            List<File> files = ArtifactFiles.unzip(new ByteArrayInputStream(zip(
                    "out/", "",
                    "out/boot.img", "kernel",
                    "AnyKernel3.zip", "zip",
                    "../../evil.txt", "nope",
                    "other/boot.img", "second")), dir);
            assertEquals(4, files.size());
            for (File f : files) assertEquals(dir, f.getParentFile());
            assertEquals("boot.img", files.get(0).getName());
            assertEquals("AnyKernel3.zip", files.get(1).getName());
            assertEquals("evil.txt", files.get(2).getName());
            assertEquals("boot (2).img", files.get(3).getName());
            assertEquals("second", new String(Files.readAllBytes(files.get(3).toPath()), StandardCharsets.UTF_8));
            assertFalse(outside.exists());
        } finally {
            ArtifactFiles.deleteTree(dir);
        }
        assertFalse(dir.exists());
    }

    @Test
    public void names() {
        assertEquals("a.zip", ArtifactFiles.fileName("x/y/a.zip"));
        assertEquals("a.zip", ArtifactFiles.fileName("x\\a.zip"));
        assertNull(ArtifactFiles.fileName("x/.."));
        assertNull(ArtifactFiles.fileName("x/"));
        Set<String> used = new HashSet<>();
        assertEquals("a.txt", ArtifactFiles.uniqueName("a.txt", used));
        assertEquals("A (2).txt", ArtifactFiles.uniqueName("A.txt", used));
        assertTrue(used.contains("a (2).txt"));
    }
}
