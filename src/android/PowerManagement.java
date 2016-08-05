/*
 * Copyright 2013-2014 Wolfgang Koller
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Cordova (Android) plugin for accessing the power-management functions of the device
 * <p>
 * based on the work of Wolfgang Koller <viras@users.sourceforge.net>
 */
package org.apache.cordova.powermanagement;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.os.PowerManager;
import android.net.wifi.WifiManager;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

/**
 * Plugin class which does the actual handling
 */
public class PowerManagement extends CordovaPlugin {
    // As we only allow one wake-lock, we keep a reference to it here
    private PowerManager.WakeLock wakeLock = null;
    private WifiManager.WifiLock wifiLock = null;
    private PowerManager powerManager = null;
    private WifiManager wifiManager = null;
    private boolean releaseOnPause = true;

    /**
     * Fetch a reference to the power-service when the plugin is initialized
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        this.powerManager = (PowerManager) cordova.getActivity().getSystemService(Context.POWER_SERVICE);
        this.wifiManager = (WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {

        PluginResult result = null;

        if (action.equals("acquire")) {
            if ("partial".equals(args.optString(0))) {
                result = this.acquire(PowerManager.PARTIAL_WAKE_LOCK);
            } else if ("wifi".equals(args.optString(0))) {
                result = this.acquireWifi(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WifiLock");
            } else {
                result = this.acquire(PowerManager.FULL_WAKE_LOCK);
            }
        } else if (action.equals("release")) {
            if ("wifi".equals(args.optString(0))) {
                result = this.releaseWifi();
            } else {
                result = this.release();
            }
        } else if (action.equals("setReleaseOnPause")) {
            try {
                this.releaseOnPause = args.getBoolean(0);
                result = new PluginResult(PluginResult.Status.OK);
            } catch (Exception e) {
                result = new PluginResult(PluginResult.Status.ERROR, "Could not set releaseOnPause");
            }
        }

        callbackContext.sendPluginResult(result);
        return true;
    }

    /**
     * Acquire a wake-lock
     * @param p_flags Type of wake-lock to acquire
     * @return PluginResult containing the status of the acquire process
     */
    private PluginResult acquire(int p_flags) {
        PluginResult result;

        if (this.wakeLock == null) {
            this.wakeLock = this.powerManager.newWakeLock(p_flags, "PowerManagementPlugin");
            try {
                this.wakeLock.acquire();
                result = new PluginResult(PluginResult.Status.OK);
            } catch (Exception e) {
                this.wakeLock = null;
                result = new PluginResult(PluginResult.Status.ERROR, "Can't acquire wake-lock - check your permissions!");
            }
        } else {
            result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "WakeLock already active - release first");
        }

        return result;
    }

    /**
     * Acquire a wifi-lock
     * @param lockType Type of wifi-lock to acquire
     * @param tag String representing a tag for the lock
     * @return PluginResult containing the status of the acquire process
     */
    private PluginResult acquireWifi(int lockType, String tag) {
        PluginResult result;

        if (this.wifiLock == null) {
            this.wifiLock = this.wifiManager.createWifiLock(lockType, tag);
            this.wifiLock.setReferenceCounted(false);
            try {
                this.wifiLock.acquire();
                result = new PluginResult(PluginResult.Status.OK);
            } catch (Exception e) {
                this.wifiLock = null;
                result = new PluginResult(PluginResult.Status.ERROR, "Can't acquire wifi-lock - check your permissions!");
            }
        } else {
            result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "WifiLock already active - release first");
        }

        return result;
    }

    /**
     * Release an active wake-lock
     * @return PluginResult containing the status of the release process
     */
    private PluginResult release() {
        PluginResult result;

        if (this.wakeLock != null) {
            try {
                this.wakeLock.release();
                result = new PluginResult(PluginResult.Status.OK, "OK");
            } catch (Exception e) {
                result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "WakeLock already released");
            }

            this.wakeLock = null;
        } else {
            result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "No WakeLock active - acquire first");
        }

        return result;
    }

    /**
     * Release an active wifi-lock
     * @return PluginResult containing the status of the release process
     */
    private PluginResult releaseWifi() {
        PluginResult result;

        if (this.wifiLock != null && this.wifiLock.isHeld()) {
            try {
                this.wifiLock.release();
                result = new PluginResult(PluginResult.Status.OK, "OK");
            } catch (Exception e) {
                result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "WifiLock already released");
            }

            this.wifiLock = null;
        } else {
            result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "No WifiLock active - acquire first");
        }

        return result;
    }

    /**
     * Make sure any wakelock is released if the app goes into pause
     */
    @Override
    public void onPause(boolean multitasking) {
        if (this.releaseOnPause && this.wakeLock != null) {
            this.wakeLock.release();
        }

        super.onPause(multitasking);
    }

    /**
     * Make sure any wakelock is acquired again once we resume
     */
    @Override
    public void onResume(boolean multitasking) {
        if (this.releaseOnPause && this.wakeLock != null) {
            this.wakeLock.acquire();
        }

        super.onResume(multitasking);
    }
}
