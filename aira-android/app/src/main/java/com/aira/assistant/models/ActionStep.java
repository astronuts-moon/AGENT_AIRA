package com.aira.assistant.models;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a single step in a device action sequence.
 */
public class ActionStep {

    @SerializedName("step")
    private String step;

    @SerializedName("element_id")
    private String elementId;

    @SerializedName("text")
    private String text;

    @SerializedName("direction")
    private String direction;

    @SerializedName("steps")
    private int steps = 1;

    public String getStep() { return step; }
    public String getElementId() { return elementId; }
    public String getText() { return text; }
    public String getDirection() { return direction; }
    public int getSteps() { return steps; }

    public void setStep(String step) { this.step = step; }
    public void setElementId(String elementId) { this.elementId = elementId; }
    public void setText(String text) { this.text = text; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setSteps(int steps) { this.steps = steps; }
}
