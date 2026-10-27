package app.rh.downloader;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

// kumpulan fungsi http simple, ga pake library luar sama sekali
// biar aman kompatibel sama java 7 di AIDE
public class HttpUtil {

    private static final String DEFAULT_UA =
	"Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static class HttpResult {
        public int code;
        public String body;
        public String finalUrl;
        public Map<String, java.util.List<String>> headers;
    }

    // request GET biasa, return body sebagai string
    public static HttpResult get(String urlStr, Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = readStream(is);

            HttpResult r = new HttpResult();
            r.code = code;
            r.body = body;
            r.finalUrl = conn.getURL().toString();
            r.headers = conn.getHeaderFields();
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // request POST form-urlencoded
    public static HttpResult postForm(String urlStr, Map<String, String> params, Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }

            StringBuilder sb = new StringBuilder();
            if (params != null) {
                boolean first = true;
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
                    first = false;
                }
            }

            OutputStream os = conn.getOutputStream();
            DataOutputStream dos = new DataOutputStream(os);
            dos.writeBytes(sb.toString());
            dos.flush();
            dos.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = readStream(is);

            HttpResult r = new HttpResult();
            r.code = code;
            r.body = body;
            r.finalUrl = conn.getURL().toString();
            r.headers = conn.getHeaderFields();
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // request POST json
    public static HttpResult postJson(String urlStr, String jsonBody, Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            conn.setRequestProperty("Content-Type", "application/json");
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = readStream(is);

            HttpResult r = new HttpResult();
            r.code = code;
            r.body = body;
            r.finalUrl = conn.getURL().toString();
            r.headers = conn.getHeaderFields();
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // download file ke path tujuan, return ukuran file (bytes)
    public static long downloadToFile(String urlStr, java.io.File dest, Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new IOException("gagal download, http code: " + code);
            }

            InputStream in = conn.getInputStream();
            java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                total += len;
            }
            out.flush();
            out.close();
            in.close();
            return total;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int len;
        while ((len = reader.read(buf)) != -1) {
            sb.append(buf, 0, len);
        }
        reader.close();
        return sb.toString();
    }

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}

