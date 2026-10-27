package app.rh.downloader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * port pipeline ytmp3.js ke java agar youtube bisa jalan langsung dari apk.
 */
public class YoutubeBackend {

    // key kosong, diambil dari halaman saat pertama dipakai dan diperbarui kalau mati
    private static String API_KEY = "";
    private static final String ENDPOINT = "epsiloncloud.org";
    private static final String ORIGIN = "https://yt-to-mp3.net";
    private static final String REFERER = "https://yt-to-mp3.net/";

    public static JSONObject download(String url, String format) throws Exception {
        if (!"mp3".equals(format) && !"mp4".equals(format)) {
            throw new Exception("format youtube cuma mp3 atau mp4");
        }

        String videoId = extractVideoId(url);
        if (videoId == null) throw new Exception("url youtube tidak valid");

        String key = getAuthKey();
        String convertUrl = getConvertUrl(key);
        JSONObject result = doConvert(convertUrl, videoId, format, 0);

        if (result.optString("progressURL").length() > 0) {
            result = pollProgress(result.optString("progressURL"), result.optString("downloadURL"), 40);
        }

        String downloadUrl = result.optString("downloadURL");
        if (downloadUrl.length() == 0) {
            throw new Exception("gak dapet download url dari backend youtube");
        }

        downloadUrl = appendParam(downloadUrl, "v", videoId);
        downloadUrl = appendParam(downloadUrl, "f", format);

        return result.put("videoId", videoId)
            .put("format", format)
            .put("sourceUrl", url)
            .put("downloadURL", downloadUrl);
    }

    public static JSONObject saveToFile(JSONObject result, File directory) throws Exception {
        if (result == null) throw new Exception("hasil youtube kosong");
        if (directory == null) throw new Exception("folder tujuan kosong");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("gagal membuat folder youtube");
        }

        String format = result.optString("format", "mp3");
        String title = result.optString("title", "youtube");
        String videoId = result.optString("videoId", "youtube");
        String fallback = sanitizeFilename(title);
        if (fallback.length() == 0) fallback = videoId;

        File temp = new File(directory, ".rh_tmp_" + videoId + "_" + System.currentTimeMillis() + ".part");
        FileOutputStream out = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(result.getString("downloadURL"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(180000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Origin", ORIGIN);
            conn.setRequestProperty("Referer", REFERER);

            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) throw new Exception("download youtube gagal status " + code);

            String backendName = extractFilename(conn.getHeaderField("Content-Disposition"));
            String fileName;
            if (backendName != null && backendName.length() > 0) {
                fileName = sanitizeFilename(removeExtension(backendName));
            } else {
                fileName = fallback;
            }
            if (fileName.length() == 0) fileName = videoId;
            fileName = uniqueName(directory, fileName, format);

            InputStream in = conn.getInputStream();
            out = new FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                total += len;
            }
            out.flush();
            in.close();
            out.close();
            out = null;

            File finalFile = new File(directory, fileName);
            if (!temp.renameTo(finalFile)) {
                copyFile(temp, finalFile);
                temp.delete();
            }

            JSONObject file = new JSONObject();
            file.put("name", finalFile.getName());
            file.put("path", finalFile.getAbsolutePath());
            file.put("size", total > 0 ? total : finalFile.length());
            file.put("kind", "mp3".equals(format) ? "audio" : "video");
            return file;
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
            if (temp.exists()) temp.delete();
            if (conn != null) conn.disconnect();
        }
    }

    // ambil api key terbaru dari halaman yt-to-mp3.net
    private static String fetchLatestApiKey() throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(ORIGIN + "/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "text/html");

            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("gagal ambil halaman, status " + code);

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String html = sb.toString();

            // cari apiKey='....' atau apiKey="...."
            Pattern p = Pattern.compile("apiKey\\s*=\\s*['\"]([^'\"]+)['\"]");
            Matcher m = p.matcher(html);
            if (m.find()) {
                String newKey = m.group(1);
                if (newKey != null && newKey.length() > 10) {
                    API_KEY = newKey;
                    return newKey;
                }
            }
            throw new Exception("api key baru tidak ditemukan di halaman");
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    // pakai key yang ada, kalau kosong atau ditolak ambil key baru lalu coba sekali lagi
    private static String getAuthKey() throws Exception {
        if (API_KEY == null || API_KEY.length() == 0) {
            fetchLatestApiKey();
        }
        try {
            return doAuth();
        } catch (Exception e) {
            fetchLatestApiKey();
            return doAuth();
        }
    }

    private static String doAuth() throws Exception {
        String url = "https://epsilon." + ENDPOINT + "/api/v1/auth?api_key=" + API_KEY + "&_=" + System.currentTimeMillis();
        HttpUtil.HttpResult res = request(url, null);

        // deteksi kalo response html (key mati)
        if (res.body != null && res.body.trim().startsWith("<")) {
            throw new Exception("auth youtube gagal (html 403): key kemungkinan mati");
        }

        JSONObject body = new JSONObject(res.body);
        if (res.code != 200 || (body.optInt("err", 0) > 0)) {
            throw new Exception("auth youtube gagal: " + res.body);
        }
        String key = body.optString("key");
        if (key.length() == 0) throw new Exception("backend youtube tidak memberi auth key");
        return key;
    }

    private static String getConvertUrl(String key) throws Exception {
        String url = "https://epsilon." + ENDPOINT + "/api/v1/init?_=" + System.currentTimeMillis();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + key);
        HttpUtil.HttpResult res = request(url, headers);
        JSONObject body = new JSONObject(res.body);
        if (res.code != 200 || body.optInt("error", 0) > 0) {
            throw new Exception("init youtube gagal: " + res.body);
        }
        String convertUrl = body.optString("convertURL");
        if (convertUrl.length() == 0) throw new Exception("backend youtube tidak memberi convertURL");
        return convertUrl;
    }

    private static JSONObject doConvert(String convertUrl, String videoId, String format, int depth) throws Exception {
        if (depth > 5) throw new Exception("terlalu banyak redirect dari backend youtube");

        String base = convertUrl;
        int oldV = base.indexOf("&v=");
        if (oldV > -1) base = base.substring(0, oldV);

        String url = appendParam(base, "v", videoId);
        url = appendParam(url, "f", format);
        url = appendParam(url, "_", String.valueOf(System.currentTimeMillis()));

        HttpUtil.HttpResult res = request(url, null);
        JSONObject body = new JSONObject(res.body);
        if (res.code != 200 || (body.optString("error").length() > 0 && !"0".equals(body.optString("error")))) {
            throw new Exception("convert youtube gagal: " + res.body);
        }

        if (body.optInt("redirect", 0) == 1 && body.optString("redirectURL").length() > 0) {
            return doConvert(body.optString("redirectURL"), videoId, format, depth + 1);
        }
        return body;
    }

    private static JSONObject pollProgress(String progressUrl, String originalDownloadUrl, int maxTry) throws Exception {
        String lastUrl = originalDownloadUrl;
        for (int i = 0; i < maxTry; i++) {
            String url = appendParam(progressUrl, "_", String.valueOf(System.currentTimeMillis()));
            HttpUtil.HttpResult res = request(url, null);
            JSONObject body = new JSONObject(res.body);
            if (res.code != 200) throw new Exception("progress youtube error status " + res.code);
            if (body.optInt("error", 0) > 0) throw new Exception("progress youtube error: " + res.body);

            String direct = body.optString("downloadURL");
            if (direct.length() > 0) lastUrl = direct;

            if (body.has("progress") && body.optDouble("progress", 0) >= 3) {
                JSONObject done = new JSONObject(body.toString());
                if (lastUrl != null && lastUrl.length() > 0) done.put("downloadURL", lastUrl);
                return done;
            }
            if (direct.length() > 0) return body;

            try { Thread.sleep(3000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Exception("download youtube dibatalkan");
            }
        }
        throw new Exception("timeout nunggu progress youtube");
    }

    private static HttpUtil.HttpResult request(String url, Map<String, String> extra) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Origin", ORIGIN);
        headers.put("Referer", REFERER);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        if (extra != null) headers.putAll(extra);
        return HttpUtil.get(url, headers);
    }

    private static String extractVideoId(String url) {
        if (url == null) return null;
        Matcher m = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:embed/|live/|shorts/)|[?&]v=)([a-zA-Z0-9-_]{11})"
        ).matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static boolean isYoutubeUrl(String url) {
        return extractVideoId(url) != null;
    }

    private static String appendParam(String url, String key, String value) {
        String sep = url.indexOf('?') >= 0 ? "&" : "?";
        return url + sep + HttpUtil.urlEncode(key) + "=" + HttpUtil.urlEncode(value);
    }

    private static String extractFilename(String contentDisposition) {
        if (contentDisposition == null) return null;
        try {
            Matcher star = Pattern.compile("filename\\*=UTF-8''([^;]+)").matcher(contentDisposition);
            if (star.find()) return URLDecoder.decode(star.group(1), "UTF-8");
        } catch (Exception ignored) {
        }
        Matcher normal = Pattern.compile("filename=\\\"?([^\\\";]+)\\\"?").matcher(contentDisposition);
        return normal.find() ? normal.group(1).trim() : null;
    }

    private static String removeExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "";
        String value = name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        value = value.replaceAll("\\s+", " ");
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        if (value.length() > 150) value = value.substring(0, 150).trim();
        return value;
    }

    private static String uniqueName(File dir, String base, String extension) {
        String name = base + "." + extension;
        int n = 2;
        while (new File(dir, name).exists()) {
            name = base + " (" + n + ")." + extension;
            n++;
        }
        return name;
    }

    private static void copyFile(File source, File target) throws Exception {
        InputStream in = new java.io.FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        out.close();
        in.close();
    }
}
