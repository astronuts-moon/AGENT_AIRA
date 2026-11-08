package com.aira.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * First-run consent activity that explains each permission before requesting it.
 * Covers: RECORD_AUDIO, SYSTEM_ALERT_WINDOW, Accessibility Service.
 */
public class ConsentActivity extends AppCompatActivity {

    private static final int REQ_AUDIO = 101;

    private int currentStep = 0;

    // Step definitions
    private static final int STEP_WELCOME         = 0;
    private static final int STEP_RECORD_AUDIO    = 1;
    private static final int STEP_OVERLAY         = 2;
    private static final int STEP_ACCESSIBILITY   = 3;
    private static final int STEP_DONE            = 4;

    private TextView tvTitle, tvDesc, tvStepIndicator;
    private ImageView ivIcon;
    private Button btnPrimary, btnSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        tvTitle        = findViewById(R.id.tv_consent_title);
        tvDesc         = findViewById(R.id.tv_consent_desc);
        tvStepIndicator = findViewById(R.id.tv_step_indicator);
        ivIcon         = findViewById(R.id.iv_consent_icon);
        btnPrimary     = findViewById(R.id.btn_consent_primary);
        btnSkip        = findViewById(R.id.btn_consent_skip);

        showStep(STEP_WELCOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check permission states when returning from system settings
        if (currentStep == STEP_OVERLAY && Settings.canDrawOverlays(this)) {
            showStep(STEP_ACCESSIBILITY);
        } else if (currentStep == STEP_ACCESSIBILITY && isAccessibilityEnabled()) {
            showStep(STEP_DONE);
        }
    }

    // ── Step Navigation ───────────────────────────────────────────────────────

    private void showStep(int step) {
        currentStep = step;
        btnSkip.setVisibility(View.VISIBLE);

        switch (step) {
            case STEP_WELCOME:
                tvStepIndicator.setText("1 / 4");
                tvTitle.setText("Welcome to AIRA");
                tvDesc.setText(
                        "AIRA is your personal AI assistant that understands your voice and " +
                        "controls your phone.\n\nWe need a few permissions to get started. " +
                        "Each one will be explained clearly."
                );
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info);
                btnPrimary.setText("Get Started");
                btnPrimary.setOnClickListener(v -> showStep(STEP_RECORD_AUDIO));
                btnSkip.setVisibility(View.GONE);
                break;

            case STEP_RECORD_AUDIO:
                tvStepIndicator.setText("2 / 4");
                tvTitle.setText("Microphone Access");
                tvDesc.setText(
                        "🎤 Why AIRA needs this:\n\n" +
                        "AIRA listens to your voice ONLY when you tap the mic button. " +
                        "Your audio is transcribed on your device and NEVER sent to any server. " +
                        "Only the text of your command is sent."
                );
                ivIcon.setImageResource(android.R.drawable.ic_btn_speak_now);
                btnPrimary.setText("Grant Microphone Permission");
                btnPrimary.setOnClickListener(v -> requestAudioPermission());
                break;

            case STEP_OVERLAY:
                tvStepIndicator.setText("3 / 4");
                tvTitle.setText("Floating Button");
                tvDesc.setText(
                        "🪟 Why AIRA needs this:\n\n" +
                        "AIRA shows a small floating mic button so you can give voice commands " +
                        "from ANY app without switching back to AIRA.\n\n" +
                        "This is the same feature used by apps like Facebook Messenger's Chat Heads."
                );
                ivIcon.setImageResource(android.R.drawable.ic_menu_compass);
                btnPrimary.setText("Allow Draw Over Other Apps");
                btnPrimary.setOnClickListener(v -> openOverlaySettings());
                break;

            case STEP_ACCESSIBILITY:
                tvStepIndicator.setText("4 / 4");
                tvTitle.setText("Accessibility Service");
                tvDesc.setText(
                        "♿ Why AIRA needs this:\n\n" +
                        "This allows AIRA to open apps, tap buttons, and type text on your behalf " +
                        "when you give a voice command like 'Send a WhatsApp message to Amma'.\n\n" +
                        "AIRA ONLY acts when you give a command. It does NOT monitor your screen or " +
                        "record any activity."
                );
                ivIcon.setImageResource(android.R.drawable.ic_menu_manage);
                btnPrimary.setText("Enable Accessibility Service");
                btnPrimary.setOnClickListener(v -> openAccessibilitySettings());
                break;

            case STEP_DONE:
                tvStepIndicator.setText("✓ Done");
                tvTitle.setText("You're all set!");
                tvDesc.setText(
                        "AIRA is ready to assist you.\n\n" +
                        "Tap the floating mic button on your screen and say something like:\n\n" +
                        "• \"YouTube-ல் பாட்டு போடு\"\n" +
                        "• \"WhatsApp-ல் Amma-கிட்ட message அனுப்பு\"\n" +
                        "• \"Weather என்ன?\""
                );
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info);
                btnPrimary.setText("Open AIRA Dashboard");
                btnSkip.setVisibility(View.GONE);
                btnPrimary.setOnClickListener(v -> {
                    // Mark consent complete
                    getSharedPreferences("aira_prefs", MODE_PRIVATE)
                            .edit().putBoolean("consent_done", true).apply();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
                break;
        }
    }

    // ── Permission Requests ───────────────────────────────────────────────────

    private void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            showStep(STEP_OVERLAY);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }
    }

    private void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            showStep(STEP_ACCESSIBILITY);
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        // onResume() will check and advance if granted
    }

    private void openAccessibilitySettings() {
        if (isAccessibilityEnabled()) {
            showStep(STEP_DONE);
            return;
        }
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        // onResume() will check and advance if enabled
    }

    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String myService = getPackageName() + "/.AiraAccessibilityService";
        return enabledServices != null && enabledServices.contains(myService);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            showStep(STEP_OVERLAY); // Advance regardless (user can deny and still proceed)
        }
    }
}
