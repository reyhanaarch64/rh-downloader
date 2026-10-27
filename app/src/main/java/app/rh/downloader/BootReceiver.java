package app.rh.downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action)
			|| "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            if (BackgroundDownloadService.isEnabled(context)) {
                BackgroundDownloadService.start(context, BackgroundDownloadService.getMode(context));
            }
        }
    }
}

