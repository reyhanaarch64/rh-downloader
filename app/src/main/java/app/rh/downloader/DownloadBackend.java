package app.rh.downloader;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadBackend {

    public interface ResultCallback {
        void onSuccess(JSONObject result);
        void onError(String platform, String message);
    }

    public static final String PLATFORM_TIKTOK = "Tiktok";
    public static final String PLATFORM_YOUTUBE = "Youtube";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void process(final Context ctx, final String platform, final String url, final ResultCallback cb) {
        process(ctx, platform, url, null, cb);
    }

    public static void process(final Context ctx, final String platform, final String url, final String format, final ResultCallback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!PLATFORM_TIKTOK.equals(platform) && !PLATFORM_YOUTUBE.equals(platform)) {
                        postError(cb, platform, "platform belum didukung");
                        return;
                    }

                    StorageHelper.migrateLegacyMedia(ctx, platform);

                    if (PLATFORM_YOUTUBE.equals(platform)) {
                        if (format == null || (!"mp3".equals(format) && !"mp4".equals(format))) {
                            postError(cb, platform, "pilih format youtube mp3 atau mp4");
                            return;
                        }
                        JSONObject result = YoutubeBackend.download(url, format);
                        File targetDir = "mp3".equals(format)
                            ? StorageHelper.getMediaDir(platform, StorageHelper.YOUTUBE_AUDIO_FOLDER)
                            : StorageHelper.getMediaDir(platform, StorageHelper.YOUTUBE_VIDEO_FOLDER);
                        JSONObject file = YoutubeBackend.saveToFile(result, targetDir);
                        JSONArray files = new JSONArray();
                        files.put(file);
                        result.put("platform", platform);
                        result.put("id", result.optString("videoId"));
                        result.put("alreadyExists", false);
                        result.put("files", files);
                        JSONObject meta = new JSONObject();
                        meta.put("title", result.optString("title"));
                        meta.put("videoId", result.optString("videoId"));
                        result.put("meta", meta);
                        scanFiles(ctx, files);
                        postSuccess(cb, result);
                        return;
                    }

                    String id = extractTikTokId(url);
                    if (id == null || id.length() == 0) {
                        postError(cb, platform, "gagal ambil id dari url, cek link lagi");
                        return;
                    }

                    if (MediaManager.hasMediaForId(platform, id)) {
                        JSONObject result = new JSONObject();
                        result.put("platform", platform);
                        result.put("id", id);
                        result.put("alreadyExists", true);
                        result.put("files", MediaManager.listFilesForId(ctx, platform, id));
                        postSuccess(cb, result);
                        return;
                    }

                    JSONObject result = downloadTiktok(url, id, platform);
                    result.put("platform", platform);
                    result.put("id", id);
                    result.put("alreadyExists", false);
                    scanFiles(ctx, result.optJSONArray("files"));
                    postSuccess(cb, result);
                } catch (Exception e) {
                    postError(cb, platform, e.getMessage() == null ? "terjadi kesalahan" : e.getMessage());
                }
            }
        }).start();
    }

    private static void scanFiles(Context ctx, JSONArray files) {
        if (files == null) return;
        try {
            String[] paths = new String[files.length()];
            for (int i = 0; i < files.length(); i++) paths[i] = files.getJSONObject(i).getString("path");
            MediaScannerConnection.scanFile(ctx, paths, null, null);
        } catch (Exception ignored) {
        }
    }

    private static void postSuccess(final ResultCallback cb, final JSONObject result) {
        mainHandler.post(new Runnable() {
            public void run() { cb.onSuccess(result); }
        });
    }

    private static void postError(final ResultCallback cb, final String platform, final String msg) {
        mainHandler.post(new Runnable() {
            public void run() { cb.onError(platform, msg); }
        });
    }

    private static String extractTikTokId(String url) {
        Pattern p = Pattern.compile("tiktok\\.com/(?:@[\\w.-]+/video/)?(\\d+)");
        Matcher m = p.matcher(url);
        if (m.find()) return m.group(1);
        p = Pattern.compile("(?:vt|vm)\\.tiktok\\.com/([A-Za-z0-9]+)");
        m = p.matcher(url);
        if (m.find()) return m.group(1);
        return "u" + Math.abs(url.hashCode());
    }

    private static synchronized File nextFile(File dir, String id, String extension) throws Exception {
        int index = 1;
        while (true) {
            File candidate = new File(dir, id + "-" + index + "." + extension);
            if (!candidate.exists() && candidate.createNewFile()) return candidate;
            index++;
        }
    }

    private static JSONObject downloadTiktok(String url, String id, String platform) throws Exception {
        String api = "https://www.tikwm.com/api/?url=" + HttpUtil.urlEncode(url) + "&hd=1";
        HttpUtil.HttpResult res = HttpUtil.get(api, null);
        JSONObject json = new JSONObject(res.body);
        JSONObject data = json.optJSONObject("data");
        if (data == null) throw new Exception("gagal ambil data dari tiktok");

        File photoDir = StorageHelper.getMediaDir(platform, StorageHelper.TIKTOK_PHOTO_FOLDER);
        File videoDir = StorageHelper.getMediaDir(platform, StorageHelper.TIKTOK_VIDEO_FOLDER);
        File audioDir = StorageHelper.getMediaDir(platform, StorageHelper.TIKTOK_AUDIO_FOLDER);

        JSONArray filesOut = new JSONArray();
        JSONArray images = data.optJSONArray("images");
        if (images != null && images.length() > 0) {
            for (int i = 0; i < images.length(); i++) {
                String imgUrl = images.getString(i);
                File dest = nextFile(photoDir, id, "jpg");
                try {
                    HttpUtil.downloadToFile(imgUrl, dest, null);
                } catch (Exception e) {
                    dest.delete();
                    throw e;
                }
                filesOut.put(fileEntry(dest, "image"));
            }
        } else {
            String playUrl = data.optString("play");
            if (playUrl.length() == 0) throw new Exception("url video kosong");
            File dest = nextFile(videoDir, id, "mp4");
            try {
                HttpUtil.downloadToFile(playUrl, dest, null);
            } catch (Exception e) {
                dest.delete();
                throw e;
            }
            filesOut.put(fileEntry(dest, "video"));
        }

        JSONObject musicInfo = data.optJSONObject("music_info");
        if (musicInfo != null && musicInfo.optString("play").length() > 0) {
            File audioDest = nextFile(audioDir, id, "mp3");
            try {
                HttpUtil.downloadToFile(musicInfo.optString("play"), audioDest, null);
            } catch (Exception e) {
                audioDest.delete();
                throw e;
            }
            filesOut.put(fileEntry(audioDest, "audio"));
        }

        JSONObject meta = new JSONObject();
        meta.put("title", data.optString("title"));
        meta.put("cover", data.optString("cover"));
        meta.put("duration", data.optInt("duration"));
        meta.put("playCount", data.optLong("play_count"));
        JSONObject author = data.optJSONObject("author");
        meta.put("uploader", author != null ? author.optString("nickname", author.optString("unique_id")) : "");

        JSONObject result = new JSONObject();
        result.put("meta", meta);
        result.put("files", filesOut);
        return result;
    }

    private static JSONObject fileEntry(File f, String kind) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", f.getName());
        o.put("path", f.getAbsolutePath());
        o.put("size", f.length());
        o.put("kind", kind);
        return o;
    }
}
