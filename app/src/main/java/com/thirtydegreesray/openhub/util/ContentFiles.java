package com.thirtydegreesray.openhub.util;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.model.UploadItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Files picked with the system file picker (or shared from another app), described
 * as {@link UploadItem}s for the release uploader.
 */
public final class ContentFiles {

    private ContentFiles() {
    }

    /** Intent for the system picker, several files of any type. */
    @NonNull
    public static Intent pickerIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }

    /**
     * The files of a picker result. Read access is made persistable where the provider
     * allows it, so the upload still works if it runs after this screen is gone.
     */
    @NonNull
    public static ArrayList<UploadItem> fromPickerResult(@NonNull Context context, @Nullable Intent data) {
        ArrayList<Uri> uris = new ArrayList<>();
        if (data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        }
        ArrayList<UploadItem> items = new ArrayList<>();
        for (Uri uri : uris) {
            keepReadAccess(context, uri);
            items.add(describe(context, uri));
        }
        return items;
    }

    @NonNull
    public static UploadItem describe(@NonNull Context context, @NonNull Uri uri) {
        String name = null;
        long size = -1;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {
        }
        if (StringUtils.isBlank(name)) {
            name = uri.getLastPathSegment();
            if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (StringUtils.isBlank(name)) name = "file";
        return new UploadItem(uri.toString(), assetName(name), size);
    }

    /**
     * GitHub stores asset names without path characters and turns most special
     * characters into dots anyway; cleaning them up front keeps the shown name honest.
     */
    @NonNull
    public static String assetName(@NonNull String name) {
        String clean = name.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return clean.isEmpty() ? "file" : clean;
    }

    /**
     * Passes this app's read access to the files on to the started component. Files
     * shared from another app are readable only while the receiving screen lives;
     * carried on the intent, the access goes with the upload.
     */
    public static void grantRead(@NonNull Intent intent, @NonNull List<UploadItem> items) {
        if (items.isEmpty()) return;
        ClipData clip = null;
        for (UploadItem item : items) {
            Uri uri = Uri.parse(item.getUri());
            if (clip == null) clip = ClipData.newRawUri(item.getName(), uri);
            else clip.addItem(new ClipData.Item(uri));
        }
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    /** Files of an ACTION_SEND / ACTION_SEND_MULTIPLE intent. */
    @NonNull
    @SuppressWarnings("deprecation")
    public static ArrayList<UploadItem> fromShareIntent(@NonNull Context context, @NonNull Intent intent) {
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) uris.add(uri);
        }
        if (uris.isEmpty() && intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                Uri uri = intent.getClipData().getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        }
        ArrayList<UploadItem> items = new ArrayList<>();
        for (Uri uri : uris) {
            if (!isForeignContent(context, uri)) continue;
            items.add(describe(context, uri));
        }
        return items;
    }

    /**
     * Only content:// addresses are accepted from a share. A file:// address (or one of
     * OpenHub's own providers, except the FileProvider of the Download folder) could point
     * into OpenHub's private folder, e.g. at the accounts database, and another app must
     * never get that uploaded.
     */
    static boolean isForeignContent(@NonNull Context context, @NonNull Uri uri) {
        return acceptsShared(uri.getScheme(), uri.getAuthority(), context.getPackageName());
    }

    /** {@link #isForeignContent} without Android classes (unit tested). */
    static boolean acceptsShared(@Nullable String scheme, @Nullable String authority,
                                 @NonNull String own) {
        if (!"content".equals(scheme)) return false;
        if (authority == null || authority.isEmpty()) return false;
        for (String part : authority.split(";")) {
            // OpenHub's own FileProvider shares only the public Download folder
            if (part.equals(own + ".fileProvider")) continue;
            if (part.equals(own) || part.startsWith(own + ".")) return false;
        }
        return true;
    }

    public static void keepReadAccess(@NonNull Context context, @NonNull Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // not a document Uri (e.g. shared from another app): the temporary grant is used
        }
    }

    public static void releaseReadAccess(@NonNull Context context, @NonNull Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }
}
