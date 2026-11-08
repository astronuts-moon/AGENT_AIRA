package com.aira.assistant;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.aira.assistant.models.ActionStep;
import com.aira.assistant.models.DeviceAction;

import java.util.List;

/**
 * Parses the device_action JSON from the backend and executes each step.
 * App launches are done via Intent (from this class).
 * UI interactions (click, type, scroll) are queued for AiraAccessibilityService.
 */
public class DeviceActionExecutor {

    private static final String TAG = "DeviceActionExecutor";
    // Delay between steps to let the UI settle
    private static final long STEP_DELAY_MS = 1200;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface ActionCallback {
        void onActionComplete();
        void onActionError(String error);
    }

    public DeviceActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public void execute(DeviceAction action, ActionCallback callback) {
        if (action == null) {
            if (callback != null) callback.onActionComplete();
            return;
        }

        List<ActionStep> steps = action.getActionSteps();
        if (steps == null || steps.isEmpty()) {
            if (callback != null) callback.onActionComplete();
            return;
        }

        Log.d(TAG, "Executing " + steps.size() + " steps for intent: " + action.getIntent());
        executeStepsSequentially(steps, 0, action.getTargetPackage(), callback);
    }

    private void executeStepsSequentially(
            List<ActionStep> steps, int index, String packageName, ActionCallback callback
    ) {
        if (index >= steps.size()) {
            if (callback != null) handler.post(callback::onActionComplete);
            return;
        }

        ActionStep step = steps.get(index);
        Log.d(TAG, "Step [" + index + "]: " + step.getStep());

        long delay = STEP_DELAY_MS;

        switch (step.getStep()) {
            case "launch_app":
                launchApp(packageName);
                delay = 2000; // Give app more time to open
                break;

            case "click":
                AiraAccessibilityService svc = AiraAccessibilityService.getInstance();
                if (svc != null) svc.performClick(step.getElementId());
                break;

            case "type_text":
                AiraAccessibilityService svcType = AiraAccessibilityService.getInstance();
                if (svcType != null) svcType.performTypeText(step.getText());
                break;

            case "scroll":
                AiraAccessibilityService svcScroll = AiraAccessibilityService.getInstance();
                String dir = step.getDirection() != null ? step.getDirection() : "down";
                if (svcScroll != null) svcScroll.performScroll(dir);
                break;

            case "navigate_back":
                AiraAccessibilityService svcBack = AiraAccessibilityService.getInstance();
                if (svcBack != null) svcBack.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                break;

            case "navigate_home":
                AiraAccessibilityService svcHome = AiraAccessibilityService.getInstance();
                if (svcHome != null) svcHome.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
                break;

            case "adjust_volume":
                adjustVolume(step);
                break;

            case "toggle_wifi":
                openWifiSettings();
                break;

            case "toggle_bluetooth":
                openBluetoothSettings();
                break;

            case "toggle_flashlight":
                // Handled via system shortcuts — trigger notification panel
                AiraAccessibilityService svcFlash = AiraAccessibilityService.getInstance();
                if (svcFlash != null) svcFlash.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                break;

            default:
                Log.w(TAG, "Unknown step: " + step.getStep());
                break;
        }

        // Schedule next step after delay
        long finalDelay = delay;
        handler.postDelayed(() ->
                executeStepsSequentially(steps, index + 1, packageName, callback),
                finalDelay
        );
    }

    private void launchApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "No package name provided");
            return;
        }
        try {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launchIntent);
                Log.d(TAG, "Launched: " + packageName);
            } else {
                // App not installed — open Play Store
                Log.w(TAG, "App not installed: " + packageName + ", opening Play Store");
                Intent playIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + packageName));
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(playIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "launchApp error", e);
        }
    }

    private void adjustVolume(ActionStep step) {
        android.media.AudioManager audio =
                (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        String dir = step.getDirection() != null ? step.getDirection() : "up";
        int direction = dir.equalsIgnoreCase("up")
                ? android.media.AudioManager.ADJUST_RAISE
                : android.media.AudioManager.ADJUST_LOWER;
        int times = Math.max(1, step.getSteps());
        for (int i = 0; i < times; i++) {
            audio.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    direction,
                    android.media.AudioManager.FLAG_SHOW_UI
            );
        }
    }

    private void openWifiSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void openBluetoothSettings() {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
