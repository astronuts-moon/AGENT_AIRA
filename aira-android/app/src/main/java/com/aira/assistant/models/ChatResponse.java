package com.aira.assistant.models;

import com.google.gson.annotations.SerializedName;

/**
 * Response model received from the AIRA Python backend.
 */
public class ChatResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("ai_speech_reply")
    private String aiSpeechReply;

    @SerializedName("device_action")
    private DeviceAction deviceAction;

    public String getStatus() { return status; }
    public String getAiSpeechReply() { return aiSpeechReply; }
    public DeviceAction getDeviceAction() { return deviceAction; }

    public boolean isSuccess() { return "success".equals(status); }
    public boolean hasDeviceAction() { return deviceAction != null; }
}
