package com.aira.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * AIRA Accessibility Service
 *
 * Executes UI interactions commanded by the AI (click, type, scroll).
 * This service runs persistently when enabled by the user.
 *
 * SECURITY: This service only acts when explicitly instructed by AIRA.
 * It does NOT monitor or log any user activity.
 */
public class AiraAccessibilityService extends AccessibilityService {

    private static final String TAG = "AiraAccessibility";
    private static AiraAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "AIRA Accessibility Service connected.");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 100;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't monitor events — only act on explicit commands.
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "AIRA Accessibility Service destroyed.");
    }

    public static AiraAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Find and click a node by its view resource ID (e.g. "search_icon", "send_button").
     * Tries exact match then substring match.
     */
    public void performClick(String elementId) {
        if (elementId == null || elementId.isEmpty()) {
            Log.w(TAG, "performClick: null elementId");
            return;
        }
        handler.post(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "performClick: no active window");
                return;
            }

            AccessibilityNodeInfo target = findNodeById(root, elementId);
            if (target == null) {
                // Try by text content
                target = findNodeByText(root, elementId);
            }
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked: " + elementId);
                target.recycle();
            } else {
                Log.w(TAG, "Node not found: " + elementId);
                // Fallback: click center of screen
                clickCenter();
            }
            root.recycle();
        });
    }

    /**
     * Type text into the currently focused input field.
     */
    public void performTypeText(String text) {
        if (text == null) return;
        handler.post(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            // Find focused editable node
            AccessibilityNodeInfo focused = findFocusedEditableNode(root);
            if (focused != null) {
                Bundle args = new Bundle();
                args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                Log.d(TAG, "Typed text: " + text);
                focused.recycle();
            } else {
                // Try to put text in clipboard and paste
                Log.w(TAG, "No editable node found, trying clipboard paste");
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("aira_text", text));
                }
            }
            root.recycle();
        });
    }

    /**
     * Scroll the active window up or down.
     */
    public void performScroll(String direction) {
        handler.post(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            AccessibilityNodeInfo scrollable = findScrollableNode(root);
            if (scrollable != null) {
                int action = "up".equalsIgnoreCase(direction)
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                scrollable.performAction(action);
                Log.d(TAG, "Scrolled: " + direction);
                scrollable.recycle();
            }
            root.recycle();
        });
    }

    // ── Node Search Helpers ───────────────────────────────────────────────────

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String elementId) {
        if (root == null) return null;

        // Try full resource ID (with package prefix)
        String activePackage = root.getPackageName() != null ? root.getPackageName().toString() : "";
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(
                activePackage + ":id/" + elementId);
        if (nodes != null && !nodes.isEmpty()) return nodes.get(0);

        // Try with common patterns
        String[] prefixes = {"com.google.android.youtube", "com.whatsapp", "com.android"};
        for (String prefix : prefixes) {
            nodes = root.findAccessibilityNodeInfosByViewId(prefix + ":id/" + elementId);
            if (nodes != null && !nodes.isEmpty()) return nodes.get(0);
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return null;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        return (nodes != null && !nodes.isEmpty()) ? nodes.get(0) : null;
    }

    private AccessibilityNodeInfo findFocusedEditableNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) return focused;

        // Traverse tree to find editable node
        return findFirstEditable(root);
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findFirstEditable(child);
            if (result != null) return result;
            if (child != null) child.recycle();
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findScrollableNode(child);
            if (result != null) return result;
            if (child != null) child.recycle();
        }
        return null;
    }

    private void clickCenter() {
        android.graphics.Point size = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        float cx = size.x / 2f;
        float cy = size.y / 2f;

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, cy);

        android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50);
        android.accessibilityservice.GestureDescription gesture =
                new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();
        dispatchGesture(gesture, null, null);
    }
}
