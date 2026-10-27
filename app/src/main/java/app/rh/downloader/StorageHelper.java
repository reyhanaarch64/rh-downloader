package app.rh.downloader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

public class StorageHelper {

    public static final String ROOT_FOLDER_NAME = "RHDOWN";
    public static final String TIKTOK_VIDEO_FOLDER = "VideoDownloadTiktok";
    public static final String TIKTOK_PHOTO_FOLDER = "PhotoDownloadTiktok";
    public static final String TIKTOK_AUDIO_FOLDER = "AudioDownloadTiktok";
    public static final String YOUTUBE_VIDEO_FOLDER = "video";
    public static final String YOUTUBE_AUDIO_FOLDER = "audio";

    public static File getRootDir() {
        File root = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static File getMediaDir(String platform, String mediaFolder) {
        File dir = new File(getRootDir(), platform + File.separator + mediaFolder);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String[] getMediaFolderNames(String platform) {
        if ("Youtube".equals(platform)) {
            return new String[]{YOUTUBE_AUDIO_FOLDER, YOUTUBE_VIDEO_FOLDER};
        }
        return new String[]{TIKTOK_VIDEO_FOLDER, TIKTOK_PHOTO_FOLDER, TIKTOK_AUDIO_FOLDER};
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static boolean isImageExtension(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)
            || "gif".equals(ext) || "webp".equals(ext);
    }

    private static boolean isVideoExtension(String ext) {
        return "mp4".equals(ext) || "mkv".equals(ext) || "mov".equals(ext)
            || "avi".equals(ext) || "webm".equals(ext) || "3gp".equals(ext);
    }

    private static boolean isAudioExtension(String ext) {
        return "mp3".equals(ext) || "m4a".equals(ext) || "wav".equals(ext)
            || "ogg".equals(ext) || "flac".equals(ext);
    }

    public static synchronized void migrateLegacyMedia(Context ctx, String platform) {
        // migrasi lama hanya untuk struktur tiktok versi sebelumnya.
        if (!"Tiktok".equals(platform)) return;

        try {
            File platformDir = new File(getRootDir(), platform);
            if (!platformDir.exists()) return;

            File videoDir = getMediaDir(platform, TIKTOK_VIDEO_FOLDER);
            File photoDir = getMediaDir(platform, TIKTOK_PHOTO_FOLDER);
            File audioDir = getMediaDir(platform, TIKTOK_AUDIO_FOLDER);

            File[] children = platformDir.listFiles();
            if (children == null) return;

            ArrayList<String> oldPaths = new ArrayList<String>();
            ArrayList<String> newPaths = new ArrayList<String>();

            for (File legacyDir : children) {
                if (!legacyDir.isDirectory()) continue;
                String folderName = legacyDir.getName();
                if (TIKTOK_VIDEO_FOLDER.equals(folderName)
                    || TIKTOK_PHOTO_FOLDER.equals(folderName)
                    || TIKTOK_AUDIO_FOLDER.equals(folderName)) continue;

                File[] files = legacyDir.listFiles();
                if (files == null) continue;

                int imageIndex = 1;
                int videoIndex = 1;
                int audioIndex = 1;

                for (File oldFile : files) {
                    if (!oldFile.isFile()) continue;
                    String ext = getExtension(oldFile.getName());
                    File targetDir;
                    int index;

                    if (isImageExtension(ext)) {
                        targetDir = photoDir;
                        index = imageIndex++;
                    } else if (isVideoExtension(ext)) {
                        targetDir = videoDir;
                        index = videoIndex++;
                    } else if (isAudioExtension(ext)) {
                        targetDir = audioDir;
                        index = audioIndex++;
                    } else {
                        continue;
                    }

                    String cleanExt = ext.length() > 0 ? ext : "bin";
                    File target = new File(targetDir, folderName + "-" + index + "." + cleanExt);
                    while (target.exists()) {
                        index++;
                        target = new File(targetDir, folderName + "-" + index + "." + cleanExt);
                    }

                    String oldPath = oldFile.getAbsolutePath();
                    if (oldFile.renameTo(target)) {
                        oldPaths.add(oldPath);
                        newPaths.add(target.getAbsolutePath());
                    }
                }

                File[] left = legacyDir.listFiles();
                if (left != null && left.length == 0) legacyDir.delete();
            }

            if (ctx != null) {
                try {
                    ContentResolver cr = ctx.getContentResolver();
                    for (int i = 0; i < oldPaths.size(); i++) {
                        cr.delete(MediaStore.Files.getContentUri("external"),
                            MediaStore.MediaColumns.DATA + "=?", new String[]{oldPaths.get(i)});
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (!newPaths.isEmpty()) {
                        MediaScannerConnection.scanFile(ctx,
                            newPaths.toArray(new String[newPaths.size()]), null, null);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasStoragePermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Method m = Environment.class.getMethod("isExternalStorageManager");
                Object result = m.invoke(null);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return false;
            }
        }
        int res = ctx.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        return res == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestStoragePermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    activity.startActivityForResult(intent, requestCode);
                } catch (Exception ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                Method m = Activity.class.getMethod("requestPermissions", String[].class, int.class);
                String[] perms = new String[]{
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE"
                };
                m.invoke(activity, (Object) perms, requestCode);
            } catch (Exception ignored) {
            }
        }
    }

    public static long getDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) size += getDirSize(f);
            else size += f.length();
        }
        return size;
    }

    public static boolean deleteRecursive(File dir) {
        if (dir == null || !dir.exists()) return true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteRecursive(f);
                else f.delete();
            }
        }
        return dir.delete();
    }
}
