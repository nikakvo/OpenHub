package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks a GitHub Actions artifact (always a zip) into a folder, for uploading its files
 * to a release. Release files have no folders, so every file keeps only its name; paths
 * that try to leave the folder ("../") are impossible this way. Names that appear twice get
 * " (2)". Plain Java (unit tested).
 */
public final class ArtifactFiles {

    /** Artifacts bigger than this (unpacked) are refused: a release file can be 2 GiB at most. */
    public static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;

    private ArtifactFiles() {
    }

    @NonNull
    public static List<File> unzip(@NonNull InputStream zip, @NonNull File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        List<File> files = new ArrayList<>();
        Set<String> used = new HashSet<>();
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(zip)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = uniqueName(fileName(entry.getName()), used);
                if (name == null) continue;
                File out = new File(dir, name);
                try (OutputStream os = new FileOutputStream(out)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_TOTAL) throw new IOException("Artifact is bigger than 2 GiB");
                        os.write(buffer, 0, read);
                    }
                }
                files.add(out);
            }
        }
        return files;
    }

    /** The last part of a zip entry name, cleaned like a release file name; null for none. */
    static String fileName(@NonNull String entryName) {
        String name = entryName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return null;
        return ContentFiles.assetName(name);
    }

    static String uniqueName(String name, @NonNull Set<String> used) {
        if (name == null) return null;
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; used.contains(candidate.toLowerCase(Locale.ROOT)); i++) {
            candidate = base + " (" + i + ")" + ext;
        }
        used.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    /** Deletes the folder and everything in it (quietly). */
    public static void deleteTree(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
