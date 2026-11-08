package com.aira.assistant.models;

import com.google.gson.annotations.SerializedName;

/**
 * Request model sent from Android to the AIRA Python backend.
 * Voice audio is transcribed on-device (never sent as audio).
 */
public class ChatRequest {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("voice_text")
    private String voiceText;

    @SerializedName("current_screen")
    private String currentScreen;

    public ChatRequest(String userId, String voiceText, String currentScreen) {
        this.userId = userId;
        this.voiceText = voiceText;
        this.currentScreen = currentScreen != null ? currentScreen : "unknown";
    }

    public String getUserId() { return userId; }
    public String getVoiceText() { return voiceText; }
    public String getCurrentScreen() { return currentScreen; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setVoiceText(String voiceText) { this.voiceText = voiceText; }
    public void setCurrentScreen(String currentScreen) { this.currentScreen = currentScreen; }
}
