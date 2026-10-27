package app.rh.downloader;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;

public class BackgroundDownloadService extends Service {

    public static final String ACTION_START = "app.rh.downloader.START";
    public static final String ACTION_STOP = "app.rh.downloader.STOP";
    public static final String ACTION_DOWNLOAD = "app.rh.downloader.DOWNLOAD";
    public static final String ACTION_YOUTUBE_MP3 = "app.rh.downloader.YOUTUBE_MP3";
    public static final String ACTION_YOUTUBE_MP4 = "app.rh.downloader.YOUTUBE_MP4";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_URL = "url";
    public static final String MODE_POPUP = "popup";
    public static final String MODE_NOTIFICATION = "notification";
    public static final String REMOTE_INPUT_KEY = "media_url";

    private static final String PREFS = "background_service";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_MODE = "mode";
    private static final String CHANNEL_ID = "rh_downloader_service";
    private static final int NOTIFICATION_ID = 7001;

    private static class DownloadTask {
        String platform;
        String url;
        String format;

        DownloadTask(String platform, String url, String format) {
            this.platform = platform;
            this.url = url;
            this.format = format;
        }
    }

    private final Queue<DownloadTask> queue = new LinkedList<DownloadTask>();
    private boolean downloading;
    private WindowManager windowManager;
    private ImageView bubbleView;
    private View panelView;
    private EditText panelInput;
    private TextView panelStatus;
    private LinearLayout panelFormatRow;
    private String pendingYoutubeUrl;
    private LinearLayout editMenuRow;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private int bubbleSize;

    public static void start(Context context, String mode) {
        Intent intent = new Intent(context, BackgroundDownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_MODE, mode);
        startCompat(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, BackgroundDownloadService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void download(Context context, String url) {
        Intent intent = new Intent(context, BackgroundDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_URL, url);
        startCompat(context, intent);
    }

    private static void startCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                java.lang.reflect.Method m = Context.class.getMethod("startForegroundService", Intent.class);
                m.invoke(context, intent);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean(PREF_ENABLED, false);
    }

    public static String getMode(Context context) {
        return context.getSharedPreferences(PREFS, 0).getString(PREF_MODE, MODE_NOTIFICATION);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForegroundCompat(buildNotification("service aktif"));

        if (isEnabled(this) && MODE_POPUP.equals(getMode(this))) {
            showBubbleIfAllowed();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_STOP.equals(action)) {
                disableService();
                return START_NOT_STICKY;
            }

            if (ACTION_START.equals(action)) {
                String mode = intent.getStringExtra(EXTRA_MODE);
                if (!MODE_POPUP.equals(mode) && !MODE_NOTIFICATION.equals(mode)) {
                    mode = MODE_NOTIFICATION;
                }

                getSharedPreferences(PREFS, 0).edit()
					.putBoolean(PREF_ENABLED, true)
					.putString(PREF_MODE, mode)
					.apply();

                startForegroundCompat(buildNotification("service aktif"));
                removeOverlay();
                if (MODE_POPUP.equals(mode)) {
                    showBubbleIfAllowed();
                }
            }

            if (ACTION_DOWNLOAD.equals(action)) {
                String remoteUrl = getRemoteInputUrl(intent);
                if (remoteUrl != null) {
                    handleIncomingUrl(remoteUrl);
                } else {
                    handleIncomingUrl(intent.getStringExtra(EXTRA_URL));
                }
            }

            if (ACTION_YOUTUBE_MP3.equals(action)) {
                String url = pendingYoutubeUrl;
                pendingYoutubeUrl = null;
                if (url != null) enqueue(DownloadBackend.PLATFORM_YOUTUBE, url, "mp3");
            }

            if (ACTION_YOUTUBE_MP4.equals(action)) {
                String url = pendingYoutubeUrl;
                pendingYoutubeUrl = null;
                if (url != null) enqueue(DownloadBackend.PLATFORM_YOUTUBE, url, "mp4");
            }
        }

        if (isEnabled(this)) return START_STICKY;
        return START_NOT_STICKY;
    }

    private String getRemoteInputUrl(Intent intent) {
        if (Build.VERSION.SDK_INT < 20 || intent == null) return null;
        try {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            if (results != null) {
                CharSequence value = results.getCharSequence(REMOTE_INPUT_KEY);
                if (value != null) {
                    String url = value.toString().trim();
                    return url.length() > 0 ? url : null;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void handleIncomingUrl(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.length() == 0) return;
        if (YoutubeBackend.isYoutubeUrl(url)) {
            pendingYoutubeUrl = url;
            updateNotification("youtube terdeteksi: pilih format");
            updatePanelStatus("youtube terdeteksi: pilih mp3 atau mp4");
            showYoutubeFormatChoices(url);
            return;
        }
        enqueue(DownloadBackend.PLATFORM_TIKTOK, url, null);
    }

    private void enqueue(String url) {
        handleIncomingUrl(url);
    }

    private void enqueue(String platform, String url, String format) {
        if (url == null) return;
        url = url.trim();
        if (url.length() == 0) return;
        queue.offer(new DownloadTask(platform, url, format));
        updateNotification("antrian: " + queue.size());
        updatePanelStatus("antrian: " + queue.size());
        processNext();
    }

    private void processNext() {
        if (downloading) return;

        final DownloadTask task = queue.poll();
        if (task == null) {
            if (pendingYoutubeUrl == null) updateNotification("service aktif");
            updatePanelStatus("");
            return;
        }

        downloading = true;
        String label = task.platform.equals(DownloadBackend.PLATFORM_YOUTUBE)
            ? "youtube " + task.format
            : "tiktok";
        updateNotification("mengunduh " + label + "...");
        updatePanelStatus("mengunduh " + label + "...");

        DownloadBackend.process(getApplicationContext(), task.platform, task.url, task.format,
            new DownloadBackend.ResultCallback() {
                public void onSuccess(JSONObject result) {
                    downloading = false;
                    pendingYoutubeUrl = null;
                    updateNotification("download " + (task.platform.equals(DownloadBackend.PLATFORM_YOUTUBE) ? "youtube selesai" : "tiktok selesai"));
                    updatePanelStatus("download selesai");
                    processNext();
                }

                public void onError(String platform, String message) {
                    downloading = false;
                    updateNotification("gagal: " + message);
                    updatePanelStatus("gagal: " + message);
                    processNext();
                }
            });
    }

    private Notification buildNotification(String status) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 7100, openIntent, pendingIntentFlags(false));

        Notification.Builder builder = new Notification.Builder(this)
            .setSmallIcon(app.rh.downloader.R.drawable.ic_launcher)
            .setContentTitle("rh downloader")
            .setContentText(status)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= 24 && pendingYoutubeUrl == null) {
            RemoteInput input = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("tempel link tiktok atau youtube")
                .build();
            Intent replyIntent = new Intent(this, BackgroundDownloadService.class);
            replyIntent.setAction(ACTION_DOWNLOAD);
            PendingIntent replyPendingIntent = PendingIntent.getService(this, 7101, replyIntent, pendingIntentFlags(true));
            Notification.Action replyAction = new Notification.Action.Builder(
                app.rh.downloader.R.drawable.ic_launcher,
                "download",
                replyPendingIntent)
                .addRemoteInput(input)
                .setAllowGeneratedReplies(false)
                .build();
            builder.addAction(replyAction);
        } else if (Build.VERSION.SDK_INT >= 20 && pendingYoutubeUrl != null) {
            addYoutubeNotificationAction(builder, ACTION_YOUTUBE_MP3, "mp3", 7112);
            addYoutubeNotificationAction(builder, ACTION_YOUTUBE_MP4, "mp4", 7113);
        } else {
            builder.setSubText("buka aplikasi untuk memasukkan link");
        }

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                java.lang.reflect.Method channelMethod = Notification.Builder.class.getMethod("setChannelId", String.class);
                channelMethod.invoke(builder, CHANNEL_ID);
            } catch (Exception ignored) {
            }
        }
        return builder.build();
    }

    private void addYoutubeNotificationAction(Notification.Builder builder, String action, String label, int requestCode) {
        Intent intent = new Intent(this, BackgroundDownloadService.class);
        intent.setAction(action);
        PendingIntent pending = PendingIntent.getService(this, requestCode, intent, pendingIntentFlags(false));
        Notification.Action actionObj = new Notification.Action.Builder(
            app.rh.downloader.R.drawable.ic_launcher, label, pending).build();
        builder.addAction(actionObj);
    }

    private int pendingIntentFlags(boolean mutable) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            if (mutable) {
                try {
                    flags |= PendingIntent.class.getField("FLAG_MUTABLE").getInt(null);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    flags |= PendingIntent.class.getField("FLAG_IMMUTABLE").getInt(null);
                } catch (Exception ignored) {
                }
            }
        }
        return flags;
    }

    private void startForegroundCompat(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    java.lang.reflect.Method m = Service.class.getMethod(
						"startForeground", int.class, Notification.class, int.class);
                    int type = 1;
                    try {
                        type = android.content.pm.ServiceInfo.class
							.getField("FOREGROUND_SERVICE_TYPE_DATA_SYNC").getInt(null);
                    } catch (Exception ignored) {
                    }
                    m.invoke(this, NOTIFICATION_ID, notification, type);
                    return;
                } catch (Exception ignored) {
                }
            }
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception ignored) {
        }
    }

    private void updateNotification(String status) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Object channel = channelClass.getConstructor(String.class, CharSequence.class, int.class)
				.newInstance(CHANNEL_ID, "rh downloader service", 2);
            channelClass.getMethod("setDescription", String.class)
				.invoke(channel, "service popup dan download background");
            channelClass.getMethod("enableVibration", boolean.class)
				.invoke(channel, false);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.getClass().getMethod("createNotificationChannel", channelClass)
					.invoke(nm, channel);
            }
        } catch (Exception ignored) {
        }
    }

    private void disableService() {
        getSharedPreferences(PREFS, 0).edit()
			.putBoolean(PREF_ENABLED, false)
			.remove(PREF_MODE)
			.apply();
        queue.clear();
        downloading = false;
        pendingYoutubeUrl = null;
        removeOverlay();
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            java.lang.reflect.Method m = Settings.class.getMethod("canDrawOverlays", Context.class);
            Object value = m.invoke(null, this);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private void showBubbleIfAllowed() {
        if (!canDrawOverlays()) return;
        if (bubbleView != null) return;

        bubbleSize = dp(58);
        bubbleView = new ImageView(this);
        bubbleView.setImageResource(app.rh.downloader.R.drawable.icon);
        bubbleView.setElevation(dp(5));
        bubbleView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        bubbleParams = new WindowManager.LayoutParams(
			bubbleSize,
			bubbleSize,
			overlayWindowType(),
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
			| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
			PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = getResources().getDisplayMetrics().widthPixels - bubbleSize - dp(14);
        bubbleParams.y = dp(150);

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
				private float downRawX;
				private float downRawY;
				private int startX;
				private int startY;
				private boolean moved;

				public boolean onTouch(View v, MotionEvent event) {
					if (bubbleParams == null) return false;

					if (event.getAction() == MotionEvent.ACTION_DOWN) {
						downRawX = event.getRawX();
						downRawY = event.getRawY();
						startX = bubbleParams.x;
						startY = bubbleParams.y;
						moved = false;
						return true;
					}

					if (event.getAction() == MotionEvent.ACTION_MOVE) {
						int dx = (int) (event.getRawX() - downRawX);
						int dy = (int) (event.getRawY() - downRawY);
						if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
						bubbleParams.x = startX + dx;
						bubbleParams.y = startY + dy;
						clampBubblePosition();
						try {
							windowManager.updateViewLayout(bubbleView, bubbleParams);
						} catch (Exception ignored) {
						}
						if (panelView != null) updatePanelPosition();
						return true;
					}

					if (event.getAction() == MotionEvent.ACTION_UP) {
						if (!moved) togglePanel();
						return true;
					}

					return true;
				}
			});

        try {
            windowManager.addView(bubbleView, bubbleParams);
        } catch (Exception ignored) {
            bubbleView = null;
        }
    }

    private void togglePanel() {
        if (panelView != null) {
            removePanel();
        } else {
            showInputPanel();
        }
    }

    private void showInputPanel() {
        if (bubbleView == null || panelView != null) return;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(10));
        card.setBackground(makeRoundBackground(Color.BLACK, dp(16)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("rh downloader");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));

        Button close = new Button(this);
        close.setText("×");
        close.setTextColor(Color.WHITE);
        close.setTextSize(18);
        close.setAllCaps(false);
        close.setBackgroundColor(Color.TRANSPARENT);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(36)));
        card.addView(header);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        panelInput = new EditText(this);
        panelInput.setSingleLine(true);
        panelInput.setHint("tempel link tiktok atau youtube");
        panelInput.setTextColor(Color.WHITE);
        panelInput.setHintTextColor(Color.LTGRAY);
        panelInput.setTextSize(13);
        panelInput.setPadding(dp(10), 0, dp(10), 0);
        panelInput.setBackground(makeRoundBackground(Color.rgb(35, 35, 35), dp(10)));
        inputRow.addView(panelInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button download = new Button(this);
        download.setText("download");
        download.setTextColor(Color.BLACK);
        download.setTextSize(12);
        download.setAllCaps(false);
        download.setBackground(makeRoundBackground(Color.WHITE, dp(10)));
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(dp(94), dp(48));
        downloadParams.leftMargin = dp(8);
        inputRow.addView(download, downloadParams);
        card.addView(inputRow);

        panelFormatRow = new LinearLayout(this);
        panelFormatRow.setOrientation(LinearLayout.HORIZONTAL);
        panelFormatRow.setGravity(Gravity.CENTER_VERTICAL);
        panelFormatRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams formatRowParams = new LinearLayout.LayoutParams(-1, dp(44));
        formatRowParams.topMargin = dp(7);
        Button mp3Button = buildFormatButton("youtube mp3", new View.OnClickListener() {
            public void onClick(View v) {
                if (pendingYoutubeUrl != null) {
                    String url = pendingYoutubeUrl;
                    pendingYoutubeUrl = null;
                    hideYoutubeFormatChoices();
                    enqueue(DownloadBackend.PLATFORM_YOUTUBE, url, "mp3");
                    clearPanelInput();
                }
            }
        });
        Button mp4Button = buildFormatButton("youtube mp4", new View.OnClickListener() {
            public void onClick(View v) {
                if (pendingYoutubeUrl != null) {
                    String url = pendingYoutubeUrl;
                    pendingYoutubeUrl = null;
                    hideYoutubeFormatChoices();
                    enqueue(DownloadBackend.PLATFORM_YOUTUBE, url, "mp4");
                    clearPanelInput();
                }
            }
        });
        panelFormatRow.addView(mp3Button, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams mp4Params = new LinearLayout.LayoutParams(0, -1, 1f);
        mp4Params.leftMargin = dp(6);
        panelFormatRow.addView(mp4Button, mp4Params);
        card.addView(panelFormatRow, formatRowParams);

        // menu manual buat tempel/salin/pilih semua, soalnya menu bawaan android
        // (floating toolbar) ga muncul di EditText yang ditaruh di window overlay
        editMenuRow = new LinearLayout(this);
        editMenuRow.setOrientation(LinearLayout.HORIZONTAL);
        editMenuRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams editMenuRowParams = new LinearLayout.LayoutParams(-1, dp(38));
        editMenuRowParams.topMargin = dp(6);
        editMenuRow.addView(buildEditMenuButton("pilih semua", new View.OnClickListener() {
									public void onClick(View v) {
										if (panelInput != null) panelInput.selectAll();
										hideEditMenu();
									}
								}));
        editMenuRow.addView(buildEditMenuButton("salin", new View.OnClickListener() {
									public void onClick(View v) {
										copyPanelInputSelection();
										hideEditMenu();
									}
								}));
        editMenuRow.addView(buildEditMenuButton("tempel", new View.OnClickListener() {
									public void onClick(View v) {
										pasteIntoPanelInput();
										hideEditMenu();
									}
								}));
        card.addView(editMenuRow, editMenuRowParams);

        panelInput.setOnLongClickListener(new View.OnLongClickListener() {
				public boolean onLongClick(View v) {
					toggleEditMenu();
					return true;
				}
			});

        panelStatus = new TextView(this);
        panelStatus.setTextColor(Color.LTGRAY);
        panelStatus.setTextSize(11);
        panelStatus.setPadding(dp(3), dp(7), dp(3), 0);
        card.addView(panelStatus, new LinearLayout.LayoutParams(-1, dp(24)));

        close.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					removePanel();
				}
			});

        download.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String value = panelInput == null ? "" : panelInput.getText().toString().trim();
                if (value.length() == 0) return;
                if (YoutubeBackend.isYoutubeUrl(value)) {
                    showYoutubeFormatChoices(value);
                    return;
                }
                enqueue(DownloadBackend.PLATFORM_TIKTOK, value, null);
                clearPanelInput();
            }
        });

        panelParams = new WindowManager.LayoutParams(
			dp(330),
			WindowManager.LayoutParams.WRAP_CONTENT,
			overlayWindowType(),
			WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
			| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
			PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.LEFT;
        updatePanelPosition();

        try {
            windowManager.addView(card, panelParams);
            panelView = card;
            updatePanelPosition();
            panelInput.requestFocus();
            panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
				| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            try {
                windowManager.updateViewLayout(panelView, panelParams);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
            panelView = null;
            panelInput = null;
            panelStatus = null;
            editMenuRow = null;
        }
    }

    private Button buildFormatButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.BLACK);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setBackground(makeRoundBackground(Color.WHITE, dp(12)));
        b.setOnClickListener(listener);
        return b;
    }

    private void showYoutubeFormatChoices(String url) {
        if (url == null || url.length() == 0) return;
        pendingYoutubeUrl = url;
        if (panelFormatRow != null) panelFormatRow.setVisibility(View.VISIBLE);
        updatePanelStatus("youtube terdeteksi: pilih mp3 atau mp4");
        updatePanelPosition();
        updateNotification("youtube terdeteksi: pilih format");
    }

    private void hideYoutubeFormatChoices() {
        if (panelFormatRow != null) panelFormatRow.setVisibility(View.GONE);
        updatePanelPosition();
        updatePanelStatus("");
    }

    private void clearPanelInput() {
        if (panelInput != null) panelInput.setText("");
        try {
            if (panelInput != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(panelInput.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private Button buildEditMenuButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(makeRoundBackground(Color.rgb(45, 45, 45), dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private void toggleEditMenu() {
        if (editMenuRow == null) return;
        boolean showing = editMenuRow.getVisibility() == View.VISIBLE;
        editMenuRow.setVisibility(showing ? View.GONE : View.VISIBLE);
    }

    private void hideEditMenu() {
        if (editMenuRow != null) editMenuRow.setVisibility(View.GONE);
    }

    private void copyPanelInputSelection() {
        if (panelInput == null) return;
        try {
            int start = Math.max(0, panelInput.getSelectionStart());
            int end = Math.max(0, panelInput.getSelectionEnd());
            CharSequence text = panelInput.getText();
            CharSequence toCopy = (end > start) ? text.subSequence(Math.min(start, end), Math.max(start, end)) : text;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("rh downloader", toCopy));
        } catch (Exception ignored) {
        }
    }

    private void pasteIntoPanelInput() {
        if (panelInput == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence pasteText = clip.getItemAt(0).coerceToText(this);
            if (pasteText == null) return;
            int start = Math.max(0, panelInput.getSelectionStart());
            int end = Math.max(0, panelInput.getSelectionEnd());
            panelInput.getText().replace(Math.min(start, end), Math.max(start, end), pasteText);
        } catch (Exception ignored) {
        }
    }

    private void updatePanelPosition() {
        if (panelParams == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = dp(330);
        int bubbleX = bubbleParams == null ? screenWidth - bubbleSize - dp(14) : bubbleParams.x;
        int bubbleY = bubbleParams == null ? dp(150) : bubbleParams.y;

        int x = bubbleX - panelWidth - dp(10);
        if (x < dp(8)) x = bubbleX + bubbleSize + dp(10);
        if (x + panelWidth > screenWidth - dp(8)) x = screenWidth - panelWidth - dp(8);

        int panelHeight = panelFormatRow != null && panelFormatRow.getVisibility() == View.VISIBLE ? dp(215) : dp(150);
        int y = bubbleY;
        if (y + panelHeight > screenHeight - dp(12)) y = screenHeight - panelHeight - dp(12);
        if (y < dp(12)) y = dp(12);

        panelParams.x = x;
        panelParams.y = y;

        if (panelView != null) {
            try {
                windowManager.updateViewLayout(panelView, panelParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void clampBubblePosition() {
        if (bubbleParams == null) return;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int min = dp(4);
        bubbleParams.x = Math.max(min, Math.min(bubbleParams.x, width - bubbleSize - min));
        bubbleParams.y = Math.max(min, Math.min(bubbleParams.y, height - bubbleSize - min));
    }

    private GradientDrawable makeRoundBackground(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable makeCircleBackground(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    private int overlayWindowType() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                return WindowManager.LayoutParams.class.getField("TYPE_APPLICATION_OVERLAY").getInt(null);
            } catch (Exception ignored) {
            }
        }
        try {
            return WindowManager.LayoutParams.class.getField("TYPE_PHONE").getInt(null);
        } catch (Exception ignored) {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }

    private void removeOverlay() {
        removePanel();
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {
            }
        }
        bubbleView = null;
        bubbleParams = null;
    }

    private void removePanel() {
        if (panelView != null && windowManager != null) {
            try {
                windowManager.removeView(panelView);
            } catch (Exception ignored) {
            }
        }
        panelView = null;
        panelInput = null;
        panelStatus = null;
        panelParams = null;
        editMenuRow = null;
        panelFormatRow = null;
    }

    private void updatePanelStatus(String text) {
        if (panelStatus != null) panelStatus.setText(text == null ? "" : text);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // service memakai START_STICKY supaya sistem punya kesempatan menyalakannya lagi
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }
}

