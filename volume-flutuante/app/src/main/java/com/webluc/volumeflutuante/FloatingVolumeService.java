package com.webluc.volumeflutuante;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Mantém o botão flutuante de volume sobre os outros apps. */
public class FloatingVolumeService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    static volatile boolean isRunning = false;

    private static final String CHANNEL_ID = "floating_volume";
    private static final String ACTION_STOP = "com.webluc.volumeflutuante.STOP";
    private static final int NOTIFICATION_ID = 1;
    private static final long AUTO_COLLAPSE_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable collapse = () -> setExpanded(false);

    private WindowManager windowManager;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private WindowManager.LayoutParams params;

    private View root;
    private ImageView bubble;
    private View panel;
    private SeekBar volumeSeek;
    private TextView volumeText;

    private final ContentObserver volumeObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            refreshVolume();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = Prefs.get(this);
        startInForeground();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        createFloatingView();
        prefs.registerOnSharedPreferenceChangeListener(this);
        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, volumeObserver);
        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        getContentResolver().unregisterContentObserver(volumeObserver);
        if (root != null && root.isAttachedToWindow()) {
            windowManager.removeView(root);
        }
        super.onDestroy();
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FloatingVolumeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_volume_up)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        null, getString(R.string.action_stop), stop).build())
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createFloatingView() {
        root = LayoutInflater.from(this).inflate(R.layout.floating_view, null);
        bubble = root.findViewById(R.id.bubble);
        panel = root.findViewById(R.id.panel);
        volumeSeek = root.findViewById(R.id.volumeSeek);
        volumeText = root.findViewById(R.id.volumeText);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(Prefs.KEY_X, 0);
        params.y = prefs.getInt(Prefs.KEY_Y, dp(200));

        applyAppearance();
        setupBubbleTouch();

        ((ImageButton) root.findViewById(R.id.btnUp)).setOnClickListener(
                v -> adjust(AudioManager.ADJUST_RAISE));
        ((ImageButton) root.findViewById(R.id.btnDown)).setOnClickListener(
                v -> adjust(AudioManager.ADJUST_LOWER));
        ((ImageButton) root.findViewById(R.id.btnMute)).setOnClickListener(
                v -> adjust(AudioManager.ADJUST_TOGGLE_MUTE));
        ((ImageButton) root.findViewById(R.id.btnClose)).setOnClickListener(
                v -> setExpanded(false));

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                try {
                    audioManager.setStreamVolume(Prefs.stream(prefs), progress, uiFlags());
                } catch (SecurityException e) {
                    showDndError();
                }
                updateVolumeText();
                scheduleCollapse();
            }

            @Override public void onStartTrackingTouch(SeekBar s) { handler.removeCallbacks(collapse); }
            @Override public void onStopTrackingTouch(SeekBar s) { scheduleCollapse(); }
        });

        windowManager.addView(root, params);
    }

    /** Arrastar move o botão; toque rápido abre/fecha o painel. */
    private void setupBubbleTouch() {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downRawX, downRawY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > touchSlop) dragging = true;
                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            windowManager.updateViewLayout(root, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            prefs.edit().putInt(Prefs.KEY_X, params.x)
                                    .putInt(Prefs.KEY_Y, params.y).apply();
                        } else {
                            v.performClick();
                            setExpanded(panel.getVisibility() != View.VISIBLE);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void setExpanded(boolean expanded) {
        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            refreshVolume();
            scheduleCollapse();
        } else {
            handler.removeCallbacks(collapse);
        }
    }

    private void scheduleCollapse() {
        handler.removeCallbacks(collapse);
        handler.postDelayed(collapse, AUTO_COLLAPSE_MS);
    }

    private void adjust(int direction) {
        try {
            audioManager.adjustStreamVolume(Prefs.stream(prefs), direction, uiFlags());
        } catch (SecurityException e) {
            showDndError();
        }
        refreshVolume();
        scheduleCollapse();
    }

    private int uiFlags() {
        return Prefs.systemUi(prefs) ? AudioManager.FLAG_SHOW_UI : 0;
    }

    private void showDndError() {
        Toast.makeText(this, "O Android bloqueou essa alteração (modo Não perturbe).",
                Toast.LENGTH_SHORT).show();
    }

    private void refreshVolume() {
        if (volumeSeek == null) return;
        int stream = Prefs.stream(prefs);
        volumeSeek.setMax(audioManager.getStreamMaxVolume(stream));
        volumeSeek.setProgress(audioManager.getStreamVolume(stream));
        updateVolumeText();
        boolean silent = audioManager.isStreamMute(stream)
                || audioManager.getStreamVolume(stream) == 0;
        bubble.setImageResource(silent ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
    }

    private void updateVolumeText() {
        int max = Math.max(1, volumeSeek.getMax());
        volumeText.setText(Math.round(volumeSeek.getProgress() * 100f / max) + "%");
    }

    private void applyAppearance() {
        root.setAlpha(Prefs.alpha(prefs) / 100f);
        int size = dp(Prefs.size(prefs));
        ViewGroup.LayoutParams lp = bubble.getLayoutParams();
        lp.width = size;
        lp.height = size;
        bubble.setLayoutParams(lp);
        int pad = size / 4;
        bubble.setPadding(pad, pad, pad, pad);
        refreshVolume();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (root != null && !Prefs.KEY_X.equals(key) && !Prefs.KEY_Y.equals(key)) {
            applyAppearance();
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
