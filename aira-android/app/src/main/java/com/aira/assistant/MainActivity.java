package com.aira.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.aira.assistant.models.ChatRequest;
import com.aira.assistant.models.ChatResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Date;

/**
 * AIRA Main Dashboard Activity.
 * Shows chat history, voice status, and quick action cards.
 * Also starts the FloatingMicService for overlay control.
 */
public class MainActivity extends AppCompatActivity implements VoiceManager.VoiceCallback {

    private static final String TAG = "MainActivity";
    private static final String PREF_NAME = "aira_prefs";
    private static final String KEY_USER_ID = "user_id";

    // Views
    private LinearLayout llChatContainer;
    private ScrollView svChat;
    private ImageButton btnMic;
    private TextView tvStatus, tvGreeting, tvTime;
    private CardView cardMic;

    // Managers
    private VoiceManager voiceManager;
    private DeviceActionExecutor actionExecutor;

    private boolean isListening = false;
    private boolean isProcessing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupGreeting();
        setupClickListeners();

        voiceManager = new VoiceManager(this, this);
        actionExecutor = new DeviceActionExecutor(this);

        // Start floating overlay service
        startFloatingService();

        // Show server config if not set
        checkServerConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceManager != null) voiceManager.destroy();
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        llChatContainer = findViewById(R.id.ll_chat_container);
        svChat          = findViewById(R.id.sv_chat);
        btnMic          = findViewById(R.id.btn_mic);
        tvStatus        = findViewById(R.id.tv_status);
        tvGreeting      = findViewById(R.id.tv_greeting);
        tvTime          = findViewById(R.id.tv_time);
        cardMic         = findViewById(R.id.card_mic);
    }

    private void setupGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "Good Morning ☀️";
        else if (hour < 17) greeting = "Good Afternoon 🌤️";
        else if (hour < 21) greeting = "Good Evening 🌙";
        else                greeting = "Good Night 🌟";
        tvGreeting.setText(greeting);
        tvTime.setText(DateFormat.format("hh:mm aa", new Date()));
    }

    private void setupClickListeners() {
        btnMic.setOnClickListener(v -> {
            if (isProcessing) return;
            if (isListening) {
                voiceManager.stopListening();
            } else {
                voiceManager.startListening();
            }
        });

        // Settings icon
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        // Quick action cards
        setupQuickAction(R.id.card_youtube,   "YouTube-ல் பாட்டு போடு");
        setupQuickAction(R.id.card_whatsapp,  "WhatsApp திற");
        setupQuickAction(R.id.card_weather,   "Today's weather என்ன?");
        setupQuickAction(R.id.card_reminder,  "Remind me at 8pm");
    }

    private void setupQuickAction(int cardId, String command) {
        View card = findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v -> {
                addChatBubble(command, true);
                sendToBackend(command);
            });
        }
    }

    // ── Floating Service ──────────────────────────────────────────────────────

    private void startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            Intent serviceIntent = new Intent(this, FloatingMicService.class);
            startForegroundService(serviceIntent);
        }
    }

    // ── VoiceManager Callbacks ─────────────────────────────────────────────────

    @Override
    public void onListeningStarted() {
        isListening = true;
        handler.post(() -> {
            tvStatus.setText("Listening...");
            tvStatus.setTextColor(Color.parseColor("#00F5FF"));
            animateMicPulse(true);
        });
    }

    @Override
    public void onListeningStopped() {
        isListening = false;
        isProcessing = true;
        handler.post(() -> {
            tvStatus.setText("Processing...");
            tvStatus.setTextColor(Color.parseColor("#7B2FBE"));
            animateMicPulse(false);
        });
    }

    @Override
    public void onSpeechResult(String text) {
        handler.post(() -> addChatBubble(text, true));
        sendToBackend(text);
    }

    @Override
    public void onSpeechError(String error) {
        isListening = false;
        isProcessing = false;
        handler.post(() -> {
            tvStatus.setText("Tap to speak");
            tvStatus.setTextColor(Color.parseColor("#AAAAAA"));
            animateMicPulse(false);
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onTtsComplete() {
        handler.post(() -> {
            isProcessing = false;
            tvStatus.setText("Tap to speak");
            tvStatus.setTextColor(Color.parseColor("#AAAAAA"));
        });
    }

    // ── API Communication ─────────────────────────────────────────────────────

    private void sendToBackend(String voiceText) {
        String userId = getOrCreateUserId();
        ChatRequest request = new ChatRequest(userId, voiceText, "main_dashboard");

        ApiClient.getInstance(this).getService()
                .sendChat(request)
                .enqueue(new Callback<ChatResponse>() {
                    @Override
                    public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ChatResponse cr = response.body();
                            handler.post(() -> {
                                addChatBubble(cr.getAiSpeechReply(), false);
                                voiceManager.speak(cr.getAiSpeechReply());

                                if (cr.hasDeviceAction()) {
                                    actionExecutor.execute(cr.getDeviceAction(), new DeviceActionExecutor.ActionCallback() {
                                        @Override public void onActionComplete() {}
                                        @Override public void onActionError(String error) {}
                                    });
                                }
                            });
                        } else {
                            showNetworkError();
                        }
                    }

                    @Override
                    public void onFailure(Call<ChatResponse> call, Throwable t) {
                        showNetworkError();
                    }
                });
    }

    private void showNetworkError() {
        handler.post(() -> {
            String msg = "I couldn't reach the server. Check your network or server URL.";
            addChatBubble(msg, false);
            voiceManager.speak(msg);
        });
    }

    // ── Chat Bubbles ──────────────────────────────────────────────────────────

    private void addChatBubble(String text, boolean isUser) {
        View bubble = getLayoutInflater().inflate(
                isUser ? R.layout.bubble_user : R.layout.bubble_aira,
                llChatContainer, false
        );
        TextView tv = bubble.findViewById(R.id.tv_bubble_text);
        tv.setText(text);

        // Fade-in animation
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(300);
        bubble.startAnimation(anim);

        llChatContainer.addView(bubble);

        // Scroll to bottom
        svChat.post(() -> svChat.fullScroll(View.FOCUS_DOWN));
    }

    // ── Mic Animation ─────────────────────────────────────────────────────────

    private void animateMicPulse(boolean start) {
        if (start) {
            ScaleAnimation pulse = new ScaleAnimation(
                    1f, 1.15f, 1f, 1.15f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            );
            pulse.setDuration(600);
            pulse.setRepeatCount(Animation.INFINITE);
            pulse.setRepeatMode(Animation.REVERSE);
            cardMic.startAnimation(pulse);
        } else {
            cardMic.clearAnimation();
        }
    }

    // ── Settings Dialog ───────────────────────────────────────────────────────

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        if (dialogView == null) {
            // Fallback inline dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("AIRA Settings");

            final EditText input = new EditText(this);
            input.setHint("Server URL (e.g. https://aira.onrender.com)");
            String current = getString(R.string.server_base_url);
            input.setText(current);
            builder.setView(input);

            builder.setPositiveButton("Save", (d, w) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    ApiClient.saveServerUrl(this, url);
                    Toast.makeText(this, "Server URL updated!", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return;
        }
        new AlertDialog.Builder(this).setView(dialogView).show();
    }

    private void checkServerConfig() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean urlConfigured = prefs.contains("server_base_url");
        String defaultUrl = getString(R.string.server_base_url);

        if (!urlConfigured && defaultUrl.contains("YOUR_SERVER")) {
            handler.postDelayed(() ->
                    Toast.makeText(this,
                            "⚠️ Please configure your server URL in Settings.",
                            Toast.LENGTH_LONG).show(),
                    1500
            );
        }
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
}
