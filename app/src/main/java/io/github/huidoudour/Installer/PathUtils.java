package io.github.huidoudour.Installer; // 确保这是您的正确包名

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PathUtils {
    private static final String TAG = "PathUtils";

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {
        if (uri == null) {
            Log.e(TAG, "Uri is null");
            return null;
        }
        Log.d(TAG, "Getting path for URI: " + uri.toString() + ", Authority: " + uri.getAuthority() + ", Scheme: " + uri.getScheme());


        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            Log.d(TAG, "URI is DocumentUri");
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                Log.d(TAG, "URI is ExternalStorageDocument");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                } else if ("home".equalsIgnoreCase(type) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // For Android 11 (R) and above, "home" points to the app's specific directory in "Documents" or "Downloads"
                    // This might not be directly accessible by 'pm install' if it's not a raw path.
                    // It's better to copy the file to a universally accessible location if this path is returned.
                    File homeDir = context.getExternalFilesDir(split[1]); // e.g. getExternalFilesDir("Documents")
                    if(homeDir != null) {
                        return homeDir.getAbsolutePath(); // This is app specific storage
                    }
                    // Fallback to trying to copy the file if direct path resolution is complex for 'home' type.
                    return copyFileToCache(context, uri);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !TextUtils.isEmpty(type) && type.matches("[A-Fa-f0-9]+-[A-Fa-f0-9]+")) {
                    // This could be an SD card or USB drive identified by UUID on Android Q+
                    // Constructing the path can be tricky and might require StorageVolume access.
                    // For simplicity, we might fall back to copying the file.
                    Log.w(TAG, "ExternalStorageDocument with UUID type: " + type + ". Path resolution complex, consider copying file.");
                    return copyFileToCache(context, uri);
                }


                // TODO handle non-primary volumes (e.g. SD card) for older Android versions
                // For older versions, direct path might be possible but complex.
                // It's often more reliable to copy the file.
                Log.w(TAG, "Unhandled ExternalStorageDocument type: " + type);
                return copyFileToCache(context, uri);


            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                Log.d(TAG, "URI is DownloadsDocument");
                String id = DocumentsContract.getDocumentId(uri);
                if (id != null && id.startsWith("raw:")) {
                    return id.substring(4);
                }
                // Handle cases where the ID is just a number (content://downloads/public_downloads/ID or content://downloads/my_downloads/ID)
                if (id != null && TextUtils.isDigitsOnly(id)) {
                    try {
                        final Uri contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                        String path = getDataColumn(context, contentUri, null, null);
                        if (path != null) return path;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "DownloadsDocument ID is not a valid long: " + id, e);
                    }
                }
                // If the above fails, or for other download Uris, try to copy the file.
                return copyFileToCache(context, uri);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                Log.d(TAG, "URI is MediaDocument");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else if ("document".equals(type) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI; // Or MediaStore.Files.getContentUri("external")
                }


                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            Log.d(TAG, "URI is content scheme");
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            if (isGoogleDriveUri(uri)) {
                Log.w(TAG, "Google Drive URI, direct path not available, attempting to copy.");
                return copyFileToCache(context, uri);
            }
            // For other content URIs, try to get the data column.
            // If it fails, fall back to copying.
            String path = getDataColumn(context, uri, null, null);
            return path != null ? path : copyFileToCache(context, uri);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            Log.d(TAG, "URI is file scheme");
            return uri.getPath();
        }
        Log.e(TAG, "Unable to resolve path for URI: " + uri);
        return null; // Or copyFileToCache(context, uri) as a last resort
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from content URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }


    private static String copyFileToCache(Context context, Uri uri) {
        if (uri == null) return null;
        String fileName = getFileName(context, uri);
        if (fileName == null) {
            // Generate a fallback filename if necessary
            fileName = "temp_apk_" + System.currentTimeMillis() + ".apk";
        }
        // Ensure the filename ends with .apk if it's meant to be an APK
        if (!fileName.toLowerCase().endsWith(".apk")) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex) + ".apk";
            } else {
                fileName = fileName + ".apk";
            }
        }


        File cacheDir = context.getCacheDir();
        if (cacheDir == null) {
            Log.e(TAG, "Cache directory is null.");
            return null;
        }
        File tempFile = new File(cacheDir, fileName);


        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            if (inputStream == null) {
                Log.e(TAG, "InputStream is null for URI: " + uri);
                return null;
            }
            byte[] buffer = new byte[4096]; // 4KB buffer
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            Log.d(TAG, "File copied to cache: " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error copying file to cache", e);
            if (tempFile.exists()) {
                tempFile.delete(); // Clean up partial file
            }
            return null;
        }
    }


    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        final String column = MediaStore.Files.FileColumns.DATA; // Often "_data"
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting data column for URI: " + uri, e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Drive.
     */
    public static boolean isGoogleDriveUri(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) ||
                "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
    }
}
