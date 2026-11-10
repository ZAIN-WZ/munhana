package com.munhana.graph.ocr;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal file provider so the system camera app can write a capture into our
 * cache directory without any storage permission.
 */
public class ImageProvider extends ContentProvider {

    public static final String AUTHORITY = "com.munhana.graph.images";

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String segment = uri.getLastPathSegment();
        String name = new File(segment == null ? "" : segment).getName();
        boolean write = mode != null && mode.contains("w");
        File target = write ? new File(capturedDir(), name) : resolve(name);
        int flags;
        if (write) {
            flags = ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
            flags |= (mode.contains("r") ? ParcelFileDescriptor.MODE_READ_WRITE
                    : ParcelFileDescriptor.MODE_WRITE_ONLY);
        } else {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(target, flags);
    }

    private File capturedDir() {
        File dir = new File(getContext().getCacheDir(), "captured");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File resolve(String name) {
        for (String folder : new String[]{"captured", "shared", "exports"}) {
            File f = new File(new File(getContext().getCacheDir(), folder), name);
            if (f.exists()) return f;
        }
        return new File(capturedDir(), name);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".munhana")) return "application/vnd.munhana.project";
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
