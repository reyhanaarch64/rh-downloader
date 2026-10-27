package app.rh.downloader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private WebView webView;
    private String pendingServiceMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
							 WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyImmersiveMode();

        webView = new WebView(this);
        setupWebView();
        setContentView(webView);

        if (!StorageHelper.hasStoragePermission(this)) {
            StorageHelper.requestStoragePermission(this, 1001);
        }

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new WebAppInterface(this, webView), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
    }

    public void requestService(String mode) {
        if (BackgroundDownloadService.MODE_POPUP.equals(mode)) {
            if (!canDrawOverlays()) {
                pendingServiceMode = mode;
                openOverlaySettings();
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            pendingServiceMode = mode;
            requestNotificationPermission();
            return;
        }

        BackgroundDownloadService.start(this, mode);
    }

    public void stopBackgroundService() {
        BackgroundDownloadService.stop(this);
    }

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            Method m = Settings.class.getMethod("canDrawOverlays", Context.class);
            Object result = m.invoke(null, this);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    public void openOverlaySettings() {
        try {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 2002);
        } catch (Exception e) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_OVERLAY_PERMISSION");
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 2002);
            } catch (Exception ignored) {
            }
        }
    }

    public void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public void openBatterySettings() {
        try {
            Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS");
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        openAppDetails();
    }

    public void openVivoBackgroundSettings() {
        if (!Build.MANUFACTURER.toLowerCase().contains("vivo")) {
            openAppDetails();
            return;
        }

        Intent[] intents = new Intent[]{
			makeComponentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
			makeComponentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
			makeComponentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
			makeComponentIntent("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")
        };

        for (int i = 0; i < intents.length; i++) {
            try {
                startActivity(intents[i]);
                return;
            } catch (Exception ignored) {
            }
        }

        openAppDetails();
    }

    private Intent makeComponentIntent(String packageName, String className) {
        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            Method m = PowerManager.class.getMethod("isIgnoringBatteryOptimizations", String.class);
            Object result = m.invoke(pm, getPackageName());
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkCallingOrSelfPermission("android.permission.POST_NOTIFICATIONS")
			== android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            Method m = Activity.class.getMethod("requestPermissions", String[].class, int.class);
            String[] perms = new String[]{"android.permission.POST_NOTIFICATIONS"};
            m.invoke(this, (Object) perms, 2003);
        } catch (Exception ignored) {
        }
    }

    public boolean hasPendingServiceRequest() {
        return pendingServiceMode != null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2002 && pendingServiceMode != null) {
            String mode = pendingServiceMode;
            pendingServiceMode = null;
            if (canDrawOverlays()) {
                requestService(mode);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2003 && pendingServiceMode != null) {
            String mode = pendingServiceMode;
            pendingServiceMode = null;
            if (hasNotificationPermission()) {
                BackgroundDownloadService.start(this, mode);
            }
        }
    }

    private void applyImmersiveMode() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                int flags = View.class.getField("SYSTEM_UI_FLAG_LAYOUT_STABLE").getInt(null)
					| View.class.getField("SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION").getInt(null)
					| View.class.getField("SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN").getInt(null)
					| View.class.getField("SYSTEM_UI_FLAG_HIDE_NAVIGATION").getInt(null)
					| View.class.getField("SYSTEM_UI_FLAG_FULLSCREEN").getInt(null)
					| View.class.getField("SYSTEM_UI_FLAG_IMMERSIVE_STICKY").getInt(null);
                decorView.setSystemUiVisibility(flags);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

