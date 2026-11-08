package com.aira.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages Speech-to-Text (on-device) and Text-to-Speech.
 * Audio is NEVER sent to the server — only the transcribed text is.
 */
public class VoiceManager {

    private static final String TAG = "VoiceManager";

    public interface VoiceCallback {
        void onSpeechResult(String text);
        void onSpeechError(String error);
        void onListeningStarted();
        void onListeningStopped();
        void onTtsComplete();
    }

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private VoiceCallback callback;

    public VoiceManager(Context context, VoiceCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        initTts();
    }

    // ── Text-to-Speech ────────────────────────────────────────────────────────

    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Try Tamil first, fall back to English
                int result = tts.setLanguage(new Locale("ta", "IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.0f);
                isTtsReady = true;

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        if (callback != null) callback.onTtsComplete();
                    }
                    @Override public void onError(String utteranceId) {}
                });
            } else {
                Log.e(TAG, "TTS init failed with status: " + status);
            }
        });
    }

    public void speak(String text) {
        if (!isTtsReady || tts == null) {
            Log.w(TAG, "TTS not ready");
            return;
        }
        String utteranceId = UUID.randomUUID().toString();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    public void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    // ── Speech-to-Text ────────────────────────────────────────────────────────

    public void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (callback != null) callback.onSpeechError("Speech recognition not available on this device.");
            return;
        }

        // Stop TTS while listening to avoid echo
        stopSpeaking();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (callback != null) callback.onListeningStarted();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    Log.d(TAG, "STT result: " + text);
                    if (callback != null) callback.onSpeechResult(text);
                }
                if (callback != null) callback.onListeningStopped();
            }

            @Override
            public void onError(int error) {
                String msg = getSpeechErrorMessage(error);
                Log.e(TAG, "STT error: " + msg);
                if (callback != null) {
                    callback.onSpeechError(msg);
                    callback.onListeningStopped();
                }
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN");  // Tamil first
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US");  // English fallback
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "startListening error", e);
            if (callback != null) callback.onSpeechError("Could not start listening: " + e.getMessage());
        }
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        isTtsReady = false;
    }

    private String getSpeechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No speech input matched";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown speech error (" + error + ")";
        }
    }
}
