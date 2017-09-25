/*
 * Modifications Copyright (C) 2017 The OmniROM Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
 package com.android.musicfx;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SystemService extends Service {
    private final static String TAG = "MusicFXSystemService";

    public class LocalBinder extends Binder {
        public SystemService getService() {
            return SystemService.this;
        }
    }
    private final LocalBinder mBinder = new LocalBinder();

    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                Log.i(TAG, "onReceive " + action);
                final int audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                        AudioEffect.ERROR_BAD_VALUE);
                final String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
                Log.i(TAG, "Package name: " + packageName);
                Log.i(TAG, "Audio session: " + audioSession);
                if ((audioSession == AudioEffect.ERROR_BAD_VALUE) || (audioSession < 0)) {
                    Log.w(TAG, "Invalid or missing audio session " + audioSession);
                    return;
                }
                ControlPanelEffect.openSession(context, packageName, audioSession);
            }
            if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
                Log.i(TAG, "onReceive " + action);
                final int audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                        AudioEffect.ERROR_BAD_VALUE);
                final String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
                Log.i(TAG, "Package name: " + packageName);
                Log.i(TAG, "Audio session: " + audioSession);
                if ((audioSession == AudioEffect.ERROR_BAD_VALUE) || (audioSession < 0)) {
                    Log.w(TAG, "Invalid or missing audio session " + audioSession);
                    return;
                }
                ControlPanelEffect.closeSession(context, packageName, audioSession);
            }
        }
    };

    private final BroadcastReceiver mRoutingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            Log.i(TAG, "onReceive " + action);
            final boolean prevUseHeadset = ControlPanelEffect.getParameterBoolean(context,
                    ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.headset);
            boolean useHeadset = intent.getIntExtra("state", 0) == 1;
            if (useHeadset != prevUseHeadset) {
                Log.i(TAG, "useHeadset = " + useHeadset);
                ControlPanelEffect.setParameterBoolean(context, ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.headset, useHeadset);
                context.sendBroadcast(new Intent(ControlPanelEffect.PREF_SCOPE_CHANGED));
            }
        }
    };

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            Log.i(TAG, "onReceive " + action);
            final boolean prevUseBluetooth = ControlPanelEffect.getParameterBoolean(context,
                    ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.bluetooth);
            boolean useBluetooth = prevUseBluetooth;
            if (action.equals(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_CONNECTED);

                if (state == BluetoothProfile.STATE_CONNECTED && !prevUseBluetooth) {
                    useBluetooth = true;
                } else if (prevUseBluetooth) {
                    useBluetooth = false;
                }
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                String stateExtra = BluetoothAdapter.EXTRA_STATE;
                int state = intent.getIntExtra(stateExtra, -1);

                if (state == BluetoothAdapter.STATE_OFF && prevUseBluetooth) {
                    useBluetooth = false;
                }
            }
            if (useBluetooth != prevUseBluetooth) {
                Log.i(TAG, "useBluetooth = " + useBluetooth);
                ControlPanelEffect.setParameterBoolean(context, ControlPanelEffect.GLOBAL_PREF_SCOPE,
                        ControlPanelEffect.Key.bluetooth, useBluetooth);
                context.sendBroadcast(new Intent(ControlPanelEffect.PREF_SCOPE_CHANGED));
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting service.");

        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        audioFilter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        registerReceiver(mAudioSessionReceiver, audioFilter);

        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        registerReceiver(mRoutingReceiver, intentFilter);

        final IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtReceiver, btFilter);

        // check if the last stored values reflect the current status
        final boolean prevUseBluetooth = ControlPanelEffect.getParameterBoolean(this,
                ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.bluetooth);
        final boolean prevUseHeadset = ControlPanelEffect.getParameterBoolean(this,
                ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.headset);

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean useBluetooth = audioManager.isBluetoothA2dpOn();
        boolean useHeadset = audioManager.isWiredHeadsetOn();
        Log.i(TAG, "onCreate: useBluetooth = " + useBluetooth + " useHeadset = " + useHeadset);

        if (useBluetooth != prevUseBluetooth) {
            Log.i(TAG, "onCreate: useBluetooth = " + useBluetooth);
            ControlPanelEffect.setParameterBoolean(this, ControlPanelEffect.GLOBAL_PREF_SCOPE,
                    ControlPanelEffect.Key.bluetooth, useBluetooth);
            sendBroadcast(new Intent(ControlPanelEffect.PREF_SCOPE_CHANGED));
        }
        if (useHeadset != prevUseHeadset) {
            Log.i(TAG, "onCreate: useHeadset = " + useHeadset);
            ControlPanelEffect.setParameterBoolean(this, ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.headset, useHeadset);
            sendBroadcast(new Intent(ControlPanelEffect.PREF_SCOPE_CHANGED));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service.");

        unregisterReceiver(mAudioSessionReceiver);
        unregisterReceiver(mRoutingReceiver);
        unregisterReceiver(mBtReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
