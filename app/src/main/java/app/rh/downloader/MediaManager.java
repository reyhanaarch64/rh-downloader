package app.rh.downloader;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class MediaManager {

    private static final String THUMB_CACHE_DIR = "thumb_cache";

    private static File[] getPlatformMediaDirs(String platform) {
        String[] names = StorageHelper.getMediaFolderNames(platform);
        File[] dirs = new File[names.length];
        for (int i = 0; i < names.length; i++) {
            dirs[i] = StorageHelper.getMediaDir(platform, names[i]);
        }
        return dirs;
    }

    public static JSONArray listFilesJson(Context ctx, File dir) {
        JSONArray arr = new JSONArray();
        if (dir == null || !dir.exists()) return arr;
        File[] files = dir.listFiles();
        if (files == null) return arr;

        java.util.Arrays.sort(files, new Comparator<File>() {
            public int compare(File a, File b) {
                long diff = b.lastModified() - a.lastModified();
                if (diff > 0) return 1;
                if (diff < 0) return -1;
                return 0;
            }
        });

        for (File f : files) {
            if (!f.isFile()) continue;
            try {
                JSONObject o = fileToJson(ctx, f);
                if (o != null) arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private static JSONObject fileToJson(Context ctx, File f) throws Exception {
        String kind = guessKind(f.getName());
        if ("document".equals(kind)) return null;
        JSONObject o = new JSONObject();
        o.put("name", f.getName());
        o.put("path", f.getAbsolutePath());
        o.put("size", f.length());
        o.put("lastModified", f.lastModified());
        o.put("kind", kind);
        if ("video".equals(kind)) {
            String thumb = createOrLoadVideoThumbnail(ctx, f);
            if (thumb.length() > 0) o.put("thumbnail", thumb);
        }
        return o;
    }

    public static boolean hasMediaForId(String platform, String id) {
        if (id == null || id.length() == 0) return false;
        File[] dirs = getPlatformMediaDirs(platform);
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && belongsToId(f.getName(), id)) return true;
            }
        }
        return false;
    }

    private static boolean belongsToId(String fileName, String id) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (base.equals(id)) return true;
        String prefix = id + "-";
        if (!base.startsWith(prefix)) return false;
        String suffix = base.substring(prefix.length());
        if (suffix.length() == 0) return false;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return false;
        }
        return true;
    }

    public static JSONArray listFilesForId(Context ctx, String platform, String id) {
        JSONArray arr = new JSONArray();
        if (id == null || id.length() == 0) return arr;
        File[] dirs = getPlatformMediaDirs(platform);
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile() || !belongsToId(f.getName(), id)) continue;
                try {
                    JSONObject o = fileToJson(ctx, f);
                    if (o != null) arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        return arr;
    }

    public static JSONObject listAll(Context ctx) {
        JSONObject result = new JSONObject();
        JSONArray platforms = new JSONArray();
        Set<String> validThumbKeys = new HashSet<String>();
        try {
            File root = StorageHelper.getRootDir();
            File[] platformDirs = root.listFiles();
            if (platformDirs != null) {
                for (File pDir : platformDirs) {
                    if (!pDir.isDirectory()) continue;
                    String platform = pDir.getName();
                    StorageHelper.migrateLegacyMedia(ctx, platform);

                    JSONObject platformObj = new JSONObject();
                    platformObj.put("platform", platform);
                    JSONArray items = new JSONArray();
                    String[] mediaFolders = StorageHelper.getMediaFolderNames(platform);

                    for (int i = 0; i < mediaFolders.length; i++) {
                        File mediaDir = new File(pDir, mediaFolders[i]);
                        if (!mediaDir.exists() || !mediaDir.isDirectory()) continue;
                        JSONArray filesJson = listFilesJson(ctx, mediaDir);
                        if (filesJson.length() == 0) continue;

                        JSONObject itemObj = new JSONObject();
                        itemObj.put("id", mediaFolders[i]);
                        itemObj.put("files", filesJson);
                        itemObj.put("size", StorageHelper.getDirSize(mediaDir));
                        items.put(itemObj);
                        collectVideoCacheKeys(mediaDir, validThumbKeys);
                    }

                    if (items.length() > 0) {
                        platformObj.put("items", items);
                        platformObj.put("size", StorageHelper.getDirSize(pDir));
                        platforms.put(platformObj);
                    }
                }
            }
            result.put("platforms", platforms);
            pruneOrphanThumbCache(ctx, validThumbKeys);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void collectVideoCacheKeys(File dir, Set<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && "video".equals(guessKind(f.getName()))) out.add(cacheKeyFor(f));
        }
    }

    private static File getThumbCacheDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), THUMB_CACHE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String cacheKeyFor(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode()) + ".jpg";
    }

    private static void pruneOrphanThumbCache(Context ctx, Set<String> validKeys) {
        try {
            File dir = getThumbCacheDir(ctx);
            File[] cached = dir.listFiles();
            if (cached == null) return;
            for (File c : cached) if (!validKeys.contains(c.getName())) c.delete();
        } catch (Exception ignored) {
        }
    }

    private static String createOrLoadVideoThumbnail(Context ctx, File file) {
        try {
            File cacheFile = ctx == null ? null : new File(getThumbCacheDir(ctx), cacheKeyFor(file));
            if (cacheFile != null && cacheFile.exists() && cacheFile.lastModified() >= file.lastModified()) {
                String cached = readThumbFile(cacheFile);
                if (cached.length() > 0) return cached;
            }

            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap == null) return "";

            int max = 420;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > max || height > max) {
                float scale = Math.min((float) max / width, (float) max / height);
                int newWidth = Math.max(1, Math.round(width * scale));
                int newHeight = Math.max(1, Math.round(height * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out);
            bitmap.recycle();
            byte[] bytes = out.toByteArray();
            if (cacheFile != null) writeThumbFile(cacheFile, bytes);
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void writeThumbFile(File cacheFile, byte[] bytes) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(cacheFile);
            fos.write(bytes);
        } catch (Exception ignored) {
        } finally {
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
        }
    }

    private static String readThumbFile(File cacheFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(cacheFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }

    private static void removeThumbCacheFor(Context ctx, String path) {
        try {
            File cacheFile = new File(getThumbCacheDir(ctx), cacheKeyFor(new File(path)));
            if (cacheFile.exists()) cacheFile.delete();
        } catch (Exception ignored) {
        }
    }

    public static boolean deleteFile(Context ctx, String path) {
        boolean ok = deleteFileOnly(path);
        removeFromMediaStore(ctx, path);
        removeThumbCacheFor(ctx, path);
        return ok;
    }

    public static int deleteFiles(Context ctx, String[] paths) {
        int count = 0;
        for (String p : paths) {
            if (deleteFileOnly(p)) count++;
            removeFromMediaStore(ctx, p);
            removeThumbCacheFor(ctx, p);
        }
        return count;
    }

    public static boolean deleteMediaFolder(Context ctx, String platform, String id) {
        try {
            StorageHelper.migrateLegacyMedia(ctx, platform);
            boolean changed = false;
            File[] dirs = getPlatformMediaDirs(platform);
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && belongsToId(f.getName(), id)) {
                        String path = f.getAbsolutePath();
                        if (deleteFileOnly(path)) changed = true;
                        removeFromMediaStore(ctx, path);
                        removeThumbCacheFor(ctx, path);
                    }
                }
            }
            return changed;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deletePlatform(Context ctx, String platform) {
        File dir = new File(StorageHelper.getRootDir(), platform);
        removeFolderFromMediaStore(ctx, dir);
        removeThumbCacheForDir(ctx, dir);
        return StorageHelper.deleteRecursive(dir);
    }

    public static boolean deleteAll(Context ctx) {
        File root = StorageHelper.getRootDir();
        removeFolderFromMediaStore(ctx, root);
        removeThumbCacheForDir(ctx, root);
        File[] children = root.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File c : children) if (!StorageHelper.deleteRecursive(c)) ok = false;
        }
        return ok;
    }

    public static boolean renameFile(Context ctx, String oldPath, String newName) {
        try {
            File oldFile = new File(oldPath);
            if (!oldFile.exists()) return false;
            File newFile = new File(oldFile.getParentFile(), newName);
            boolean ok = oldFile.renameTo(newFile);
            if (ok) {
                removeFromMediaStore(ctx, oldPath);
                removeThumbCacheFor(ctx, oldPath);
                MediaScannerConnection.scanFile(ctx, new String[]{newFile.getAbsolutePath()}, null, null);
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static void removeThumbCacheForDir(Context ctx, File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) removeThumbCacheForDir(ctx, f);
            else if ("video".equals(guessKind(f.getName()))) removeThumbCacheFor(ctx, f.getAbsolutePath());
        }
    }

    private static boolean deleteFileOnly(String path) {
        try {
            File f = new File(path);
            return f.exists() && f.delete();
        } catch (Exception e) {
            return false;
        }
    }

    private static void removeFromMediaStore(Context ctx, String path) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            cr.delete(MediaStore.Files.getContentUri("external"), MediaStore.MediaColumns.DATA + "=?", new String[]{path});
        } catch (Exception ignored) {
        }
    }

    private static void removeFolderFromMediaStore(Context ctx, File dir) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            cr.delete(MediaStore.Files.getContentUri("external"), MediaStore.MediaColumns.DATA + " LIKE ?", new String[]{dir.getAbsolutePath() + "/%"});
        } catch (Exception ignored) {
        }
    }

    private static String guessKind(String filename) {
        String lower = filename.toLowerCase();
        if (lower.matches(".*\\.(mp4|mkv|mov|avi|webm|3gp)$")) return "video";
        if (lower.matches(".*\\.(mp3|m4a|wav|ogg|flac)$")) return "audio";
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) return "image";
        return "document";
    }
}
