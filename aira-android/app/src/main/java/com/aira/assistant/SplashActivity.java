package com.aira.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Splash screen that checks first-run status and routes to ConsentActivity or MainActivity.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView tvTagline = findViewById(R.id.tv_splash_tagline);

        // Fade-in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(1200);
        fadeIn.setFillAfter(true);
        tvTagline.startAnimation(fadeIn);

        // Navigate after 2.5 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean consentDone = getSharedPreferences("aira_prefs", MODE_PRIVATE)
                    .getBoolean("consent_done", false);

            Intent next = consentDone
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, ConsentActivity.class);
            startActivity(next);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2500);
    }
}
