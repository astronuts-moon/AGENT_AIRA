package com.aira.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import com.aira.assistant.models.ChatRequest;
import com.aira.assistant.models.ChatResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Floating overlay service that shows the AIRA mic button on top of all apps.
 * Requires SYSTEM_ALERT_WINDOW (Draw Over Other Apps) permission.
 */
public class FloatingMicService extends Service implements VoiceManager.VoiceCallback {

    private static final String TAG = "FloatingMicService";
    private static final String CHANNEL_ID = "aira_floating_channel";
    private static final int NOTIF_ID = 1001;
    private static final String PREF_NAME = "aira_prefs";
    private static final String KEY_USER_ID = "user_id";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private VoiceManager voiceManager;
    private DeviceActionExecutor actionExecutor;

    private ImageButton btnMic;
    private TextView tvStatus;
    private boolean isListening = false;
    private boolean isProcessing = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Service Lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        voiceManager = new VoiceManager(this, this);
        actionExecutor = new DeviceActionExecutor(this);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Settings.canDrawOverlays(this)) {
            showFloatingButton();
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted");
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        }
        if (voiceManager != null) voiceManager.destroy();
    }

    // ── Floating Button UI ────────────────────────────────────────────────────

    private void showFloatingButton() {
        if (floatingView != null) return;

        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_mic, null);
        btnMic = floatingView.findViewById(R.id.btn_floating_mic);
        tvStatus = floatingView.findViewById(R.id.tv_floating_status);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = 32;
        params.y = 120;

        windowManager.addView(floatingView, params);
        setupTouchDrag();
        setupMicButton();
    }

    private void setupMicButton() {
        btnMic.setOnClickListener(v -> {
            if (isProcessing) return;
            if (isListening) {
                voiceManager.stopListening();
            } else {
                voiceManager.startListening();
            }
        });
    }

    /** Allow dragging the floating button around the screen. */
    private void setupTouchDrag() {
        final int[] initialX = {0};
        final int[] initialY = {0};
        final float[] initialTouchX = {0};
        final float[] initialTouchY = {0};
        final long[] touchDownTime = {0};

        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX[0] = params.x;
                    initialY[0] = params.y;
                    initialTouchX[0] = event.getRawX();
                    initialTouchY[0] = event.getRawY();
                    touchDownTime[0] = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = initialX[0] - (int) (event.getRawX() - initialTouchX[0]);
                    params.y = initialY[0] - (int) (event.getRawY() - initialTouchY[0]);
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    long elapsed = System.currentTimeMillis() - touchDownTime[0];
                    float dx = Math.abs(event.getRawX() - initialTouchX[0]);
                    float dy = Math.abs(event.getRawY() - initialTouchY[0]);
                    // Treat as a tap if moved less than 10px and held less than 200ms
                    if (elapsed < 200 && dx < 10 && dy < 10) {
                        btnMic.performClick();
                    }
                    return true;
            }
            return false;
        });
    }

    // ── VoiceManager Callbacks ─────────────────────────────────────────────────

    @Override
    public void onListeningStarted() {
        isListening = true;
        handler.post(() -> {
            setStatus("Listening...");
            btnMic.setColorFilter(Color.parseColor("#00F5FF"));
        });
    }

    @Override
    public void onListeningStopped() {
        isListening = false;
        handler.post(() -> {
            setStatus("Processing...");
            isProcessing = true;
            btnMic.setColorFilter(Color.parseColor("#7B2FBE"));
        });
    }

    @Override
    public void onSpeechResult(String text) {
        Log.d(TAG, "STT result: " + text);
        handler.post(() -> setStatus("\"" + text + "\""));
        sendToBackend(text);
    }

    @Override
    public void onSpeechError(String error) {
        isListening = false;
        isProcessing = false;
        Log.w(TAG, "STT error: " + error);
        handler.post(() -> {
            setStatus("Tap to speak");
            btnMic.clearColorFilter();
            voiceManager.speak("Sorry, I didn't catch that. Please try again.");
        });
    }

    @Override
    public void onTtsComplete() {
        handler.post(() -> {
            isProcessing = false;
            setStatus("Tap to speak");
            btnMic.clearColorFilter();
        });
    }

    // ── API Communication ─────────────────────────────────────────────────────

    private void sendToBackend(String voiceText) {
        String userId = getOrCreateUserId();
        String currentScreen = getCurrentScreenContext();

        ChatRequest request = new ChatRequest(userId, voiceText, currentScreen);

        ApiClient.getInstance(this).getService()
                .sendChat(request)
                .enqueue(new Callback<ChatResponse>() {
                    @Override
                    public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ChatResponse chatResponse = response.body();
                            Log.d(TAG, "API response: " + chatResponse.getAiSpeechReply());

                            handler.post(() -> {
                                // Speak the AI reply
                                voiceManager.speak(chatResponse.getAiSpeechReply());

                                // Execute device action if present
                                if (chatResponse.hasDeviceAction()) {
                                    actionExecutor.execute(chatResponse.getDeviceAction(), new DeviceActionExecutor.ActionCallback() {
                                        @Override public void onActionComplete() {
                                            Log.d(TAG, "Device action complete");
                                        }
                                        @Override public void onActionError(String error) {
                                            Log.e(TAG, "Device action error: " + error);
                                        }
                                    });
                                }
                            });
                        } else {
                            Log.e(TAG, "API error: " + response.code());
                            handler.post(() -> voiceManager.speak("Sorry, I couldn't reach my brain. Check your connection."));
                            onTtsComplete();
                        }
                    }

                    @Override
                    public void onFailure(Call<ChatResponse> call, Throwable t) {
                        Log.e(TAG, "Network failure: " + t.getMessage());
                        handler.post(() -> voiceManager.speak("Network error. Please check your internet connection."));
                        onTtsComplete();
                    }
                });
    }

    private String getOrCreateUserId() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (userId == null) {
            userId = java.util.UUID.randomUUID().toString();
            prefs.edit().putString(KEY_USER_ID, userId).apply();
        }
        return userId;
    }

    private String getCurrentScreenContext() {
        // Try to get current app package from AccessibilityService
        AiraAccessibilityService svc = AiraAccessibilityService.getInstance();
        if (svc != null) {
            android.view.accessibility.AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                String pkg = root.getPackageName().toString();
                root.recycle();
                return pkg;
            }
        }
        return "unknown";
    }

    private void setStatus(String text) {
        if (tvStatus != null) tvStatus.setText(text);
    }

    // ── Foreground Notification ───────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AIRA Assistant",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("AIRA is ready to assist you");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AIRA is active")
                .setContentText("Tap the mic button to give a voice command")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
