package app.rh.downloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class WebAppInterface {

    private Context ctx;
    private WebView webView;

    public WebAppInterface(Context ctx, WebView webView) {
        this.ctx = ctx;
        this.webView = webView;
    }

    @JavascriptInterface
    public void download(final String platform, final String url, final String requestId) {
        downloadMedia(platform, url, "", requestId);
    }

    @JavascriptInterface
    public void downloadMedia(final String platform, final String url, final String format, final String requestId) {
        DownloadBackend.process(ctx, platform, url, format, new DownloadBackend.ResultCallback() {
            public void onSuccess(JSONObject result) {
                try {
                    result.put("requestId", requestId);
                } catch (Exception ignored) {
                }
                callJs("onDownloadSuccess", result.toString());
            }

            public void onError(String platform, String message) {
                JSONObject err = new JSONObject();
                try {
                    err.put("requestId", requestId);
                    err.put("platform", platform);
                    err.put("message", message);
                } catch (Exception ignored) {
                }
                callJs("onDownloadError", err.toString());
            }
        });
    }

    @JavascriptInterface
    public void getMediaList(final String requestId) {
        new Thread(new Runnable() {
				public void run() {
					final JSONObject data = MediaManager.listAll(ctx);
					try {
						data.put("requestId", requestId);
					} catch (Exception ignored) {
					}
					callJs("onMediaList", data.toString());
				}
			}).start();
    }

    @JavascriptInterface
    public void getClipboardText(final String requestId) {
        final String[] value = new String[]{""};
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0) != null) {
                    CharSequence text = clip.getItemAt(0).coerceToText(ctx);
                    if (text != null) value[0] = text.toString();
                }
            }
        } catch (Exception ignored) {
        }

        JSONObject o = new JSONObject();
        try {
            o.put("requestId", requestId);
            o.put("text", value[0]);
        } catch (Exception ignored) {
        }
        callJs("onClipboardText", o.toString());
    }

    @JavascriptInterface
    public void startBackgroundService(String mode) {
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).requestService(mode);
        }
    }

    @JavascriptInterface
    public void stopBackgroundService() {
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).stopBackgroundService();
        } else {
            BackgroundDownloadService.stop(ctx);
        }
    }

    @JavascriptInterface
    public boolean canDrawOverlays() {
        if (ctx instanceof MainActivity) return ((MainActivity) ctx).canDrawOverlays();
        return Build.VERSION.SDK_INT < 23;
    }

    @JavascriptInterface
    public void openOverlaySettings() {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).openOverlaySettings();
    }

    @JavascriptInterface
    public boolean isBatteryOptimizedOff() {
        if (ctx instanceof MainActivity) return ((MainActivity) ctx).isIgnoringBatteryOptimizations();
        return Build.VERSION.SDK_INT < 23;
    }

    @JavascriptInterface
    public void openBatterySettings() {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).openBatterySettings();
    }

    @JavascriptInterface
    public void openVivoBackgroundSettings() {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).openVivoBackgroundSettings();
    }

    @JavascriptInterface
    public void openAppDetails() {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).openAppDetails();
    }

    @JavascriptInterface
    public void getBackgroundState(final String requestId) {
        JSONObject o = new JSONObject();
        try {
            o.put("requestId", requestId);
            o.put("running", BackgroundDownloadService.isEnabled(ctx));
            o.put("mode", BackgroundDownloadService.getMode(ctx));
            o.put("overlayAllowed", canDrawOverlays());
            o.put("batteryOptimizedOff", isBatteryOptimizedOff());
        } catch (Exception ignored) {
        }
        callJs("onBackgroundState", o.toString());
    }

    @JavascriptInterface
    public void deleteMedia(final String platform, final String id, final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					boolean ok = MediaManager.deleteMediaFolder(ctx, platform, id);
					callJsSimple("onMediaDeleted", requestId, ok);
				}
			});
    }

    @JavascriptInterface
    public void deletePlatform(final String platform, final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					boolean ok = MediaManager.deletePlatform(ctx, platform);
					callJsSimple("onPlatformDeleted", requestId, ok);
				}
			});
    }

    @JavascriptInterface
    public void deleteAllMedia(final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					boolean ok = MediaManager.deleteAll(ctx);
					callJsSimple("onAllDeleted", requestId, ok);
				}
			});
    }

    @JavascriptInterface
    public void deleteFile(final String path, final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					boolean ok = MediaManager.deleteFile(ctx, path);
					callJsSimple("onFileDeleted", requestId, ok);
				}
			});
    }

    @JavascriptInterface
    public void deleteFiles(final String pathsJson, final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					try {
						JSONArray arr = new JSONArray(pathsJson);
						String[] paths = new String[arr.length()];
						for (int i = 0; i < arr.length(); i++) paths[i] = arr.getString(i);
						int count = MediaManager.deleteFiles(ctx, paths);
						JSONObject o = new JSONObject();
						o.put("requestId", requestId);
						o.put("success", true);
						o.put("count", count);
						callJs("onFilesDeleted", o.toString());
					} catch (Exception e) {
						callJsSimple("onFilesDeleted", requestId, false);
					}
				}
			});
    }

    @JavascriptInterface
    public void renameFile(final String oldPath, final String newName, final String requestId) {
        webView.post(new Runnable() {
				public void run() {
					boolean ok = MediaManager.renameFile(ctx, oldPath, newName);
					callJsSimple("onFileRenamed", requestId, ok);
				}
			});
    }

    @JavascriptInterface
    public void shareFile(final String path) {
        webView.post(new Runnable() {
				public void run() {
					try {
						File f = new File(path);
						Uri uri = AppFileProvider.getUriForFile(f);
						String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1).toLowerCase() : "";
						String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
						if (mime == null) mime = "*/*";

						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType(mime);
						intent.putExtra(Intent.EXTRA_STREAM, uri);
						intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						Intent chooser = Intent.createChooser(intent, "Bagikan ke");
						chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						ctx.startActivity(chooser);
					} catch (Exception ignored) {
					}
				}
			});
    }

    @JavascriptInterface
    public void openAudioExternal(final String path) {
        webView.post(new Runnable() {
				public void run() {
					try {
						File f = new File(path);
						Uri uri = AppFileProvider.getUriForFile(f);
						String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1).toLowerCase() : "";
						String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
						if (mime == null) mime = "audio/*";

						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(uri, mime);
						intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						Intent chooser = Intent.createChooser(intent, "buka pakai");
						chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						ctx.startActivity(chooser);
					} catch (Exception ignored) {
					}
				}
			});
    }

    @JavascriptInterface
    public boolean hasStoragePermission() {
        return StorageHelper.hasStoragePermission(ctx);
    }

    @JavascriptInterface
    public void requestStoragePermission() {
        if (ctx instanceof MainActivity) {
            StorageHelper.requestStoragePermission((MainActivity) ctx, 1001);
        }
    }

    private void callJs(final String fnName, final String jsonArg) {
        webView.post(new Runnable() {
				public void run() {
					String escaped = jsonArg.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
					String js = "javascript:" + fnName + "(JSON.parse('" + escaped + "'))";
					webView.evaluateJavascript(js, null);
				}
			});
    }

    private void callJsSimple(String fnName, String requestId, boolean ok) {
        JSONObject o = new JSONObject();
        try {
            o.put("requestId", requestId);
            o.put("success", ok);
        } catch (Exception ignored) {
        }
        callJs(fnName, o.toString());
    }
}

