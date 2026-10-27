package app.rh.downloader;
// import
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

// provider custom buat ngasih akses content:// ke file di /sdcard/RHDOWN,
// dipake buat fitur share (pengganti androidx.core.content.FileProvider
// biar ga usah nambah dependency support library di AIDE)
public class AppFileProvider extends ContentProvider {

    public static final String AUTHORITY = "app.rh.downloader.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    // bikin content uri dari File asli, path nya relatif ke folder RHDOWN
    public static Uri getUriForFile(File file) {
        File root = StorageHelper.getRootDir();
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        String relative = filePath.startsWith(rootPath) ? filePath.substring(rootPath.length()) : file.getName();

        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTHORITY);
        String[] parts = relative.split("/");
        for (String part : parts) {
            if (part.length() > 0) b.appendPath(part);
        }
        return b.build();
    }

    private File resolveFile(Uri uri) {
        File root = StorageHelper.getRootDir();
        java.util.List<String> segments = uri.getPathSegments();
        File f = root;
        for (String seg : segments) {
            f = new File(f, seg);
        }
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolveFile(uri);
        int m = "w".equals(mode) ? ParcelFileDescriptor.MODE_WRITE_ONLY : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        ParcelFileDescriptor pfd = openFile(uri, mode);
        return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = resolveFile(uri);
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{f.getName(), f.length()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File f = resolveFile(uri);
        String name = f.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File f = resolveFile(uri);
        return f.delete() ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

