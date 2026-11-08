package com.aira.assistant.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Device action returned by the AIRA backend.
 * Executed by AiraAccessibilityService.
 */
public class DeviceAction {

    @SerializedName("intent")
    private String intent;

    @SerializedName("target_package")
    private String targetPackage;

    @SerializedName("action_steps")
    private List<ActionStep> actionSteps;

    public String getIntent() { return intent; }
    public String getTargetPackage() { return targetPackage; }
    public List<ActionStep> getActionSteps() { return actionSteps; }

    public void setIntent(String intent) { this.intent = intent; }
    public void setTargetPackage(String targetPackage) { this.targetPackage = targetPackage; }
    public void setActionSteps(List<ActionStep> actionSteps) { this.actionSteps = actionSteps; }
}
