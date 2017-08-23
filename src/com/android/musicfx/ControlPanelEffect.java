/*
 * Copyright (C) 2010-2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.musicfx;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The Common class defines constants to be used by the control panels.
 */
public class ControlPanelEffect {

    private final static String TAG = "MusicFXControlPanelEffect";
    public static final String GLOBAL_PREF_SCOPE = "com.android.musicfx";
    public static final String SPEAKER_PREF_SCOPE = "com.android.musicfx.speaker";
    public static final String HEADSET_PREF_SCOPE = "com.android.musicfx.headset";
    public static final String BLUETOOTH_PREF_SCOPE = "com.android.musicfx.bluetooth";
    public static final String PREF_SCOPE_CHANGED = "com.android.musicfx.PREF_SCOPE_CHANGED";

    public static String[] ALL_PREF_SCOPES = new String[] {SPEAKER_PREF_SCOPE, HEADSET_PREF_SCOPE, BLUETOOTH_PREF_SCOPE};
    /**
     * Audio session priority
     */
    private static final int PRIORITY = 0;

    /**
     * The control mode specifies if control panel updates effects and preferences or only
     * preferences.
     */
    static enum ControlMode {
        /**
         * Control panel updates effects and preferences. Applicable when audio session is delivered
         * by user.
         */
        CONTROL_EFFECTS,
        /**
         * Control panel only updates preferences. Applicable when there was no audio or invalid
         * session provided by user.
         */
        CONTROL_PREFERENCES
    }

    static enum Key {
        global_enabled, virt_enabled, virt_strength_supported, virt_strength, virt_type, bb_enabled,
        bb_strength, te_enabled, te_strength, avl_enabled, lm_enabled, lm_strength, eq_enabled,
        eq_num_bands, eq_level_range, eq_center_freq, eq_band_level,
        eq_num_presets, eq_preset_name, eq_preset_user_band_level,
        eq_preset_user_band_level_default, eq_current_preset,
        pr_enabled, pr_current_preset, sw_enabled, sw_strength,
        bluetooth, headset
    }

    protected static class EffectSet {

        final Equalizer mEqualizer;
        final BassBoost mBassBoost;
        final Virtualizer mVirtualizer;
        final PresetReverb mPresetReverb;
        int mAudioSession;

        protected EffectSet(int sessionId) {
            mAudioSession = sessionId;
            mEqualizer = new Equalizer(0, sessionId);
            mBassBoost = new BassBoost(0, sessionId);
            mVirtualizer = new Virtualizer(0, sessionId);
            mPresetReverb = new PresetReverb(0, sessionId);
        }

        protected void release() {
            mEqualizer.release();
            mBassBoost.release();
            mVirtualizer.release();
            mPresetReverb.release();
        }
    }

    protected static final Map<Integer, EffectSet> mAudioSessions = new HashMap<Integer, EffectSet>();

    // Defaults
    private final static boolean GLOBAL_ENABLED_DEFAULT = false;
    private final static boolean VIRTUALIZER_ENABLED_DEFAULT = false;
    private final static int VIRTUALIZER_STRENGTH_DEFAULT = 1000;
    private final static boolean BASS_BOOST_ENABLED_DEFAULT = false;
    private final static int BASS_BOOST_STRENGTH_DEFAULT = 667;
    private final static boolean PRESET_REVERB_ENABLED_DEFAULT = false;
    private final static int PRESET_REVERB_CURRENT_PRESET_DEFAULT = 0; // None

    // EQ defaults
    private final static boolean EQUALIZER_ENABLED_DEFAULT = true;
    private final static String EQUALIZER_PRESET_NAME_DEFAULT = "Preset";
    private final static short EQUALIZER_NUMBER_BANDS_DEFAULT = 5;
    private final static short EQUALIZER_NUMBER_PRESETS_DEFAULT = 0;
    private final static short[] EQUALIZER_BAND_LEVEL_RANGE_DEFAULT = { -1500, 1500 };
    private final static int[] EQUALIZER_CENTER_FREQ_DEFAULT = { 60000, 230000, 910000, 3600000,
            14000000 };
    private final static short[] EQUALIZER_PRESET_USER_BAND_LEVEL_DEFAULT = { 0, 0, 0, 0, 0 };
    private final static short[][] EQUALIZER_PRESET_OPENSL_ES_BAND_LEVEL_DEFAULT = new short[EQUALIZER_NUMBER_PRESETS_DEFAULT][EQUALIZER_NUMBER_BANDS_DEFAULT];

    // EQ effect properties which are invariable over all EQ effects sessions
    private static short[] mEQBandLevelRange = EQUALIZER_BAND_LEVEL_RANGE_DEFAULT;
    private static short mEQNumBands = EQUALIZER_NUMBER_BANDS_DEFAULT;
    private static int[] mEQCenterFreq = EQUALIZER_CENTER_FREQ_DEFAULT;
    private static short mEQNumPresets = EQUALIZER_NUMBER_PRESETS_DEFAULT;
    private static short[][] mEQPresetOpenSLESBandLevel = EQUALIZER_PRESET_OPENSL_ES_BAND_LEVEL_DEFAULT;
    private static String[] mEQPresetNames;
    private static boolean mIsInitialized = false;
    private final static Object mEQInitLock = new Object();

    /**
     * Default int argument used in methods to see that the arg is a dummy. Used for method
     * overloading.
     */
    private final static int DUMMY_ARGUMENT = -1;

    /**
     * Inits effects preferences for the given context in the control panel.
     *
     * @param context
     */
    public static void initEffectsPreferences(final Context context) {
        Log.d(TAG, "initEffectsPreferences");
        synchronized (mEQInitLock) {
            init(context);
        }
        for (String prefLevel : ALL_PREF_SCOPES) {
            final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                    Context.MODE_PRIVATE);
            // init preferences
            try {
                final SharedPreferences.Editor editor = prefs.edit();

                editor.putInt(Key.eq_level_range.toString() + 0, mEQBandLevelRange[0]);
                editor.putInt(Key.eq_level_range.toString() + 1, mEQBandLevelRange[1]);
                editor.putInt(Key.eq_num_bands.toString(), mEQNumBands);
                editor.putInt(Key.eq_num_presets.toString(), mEQNumPresets);
                // Resetting the EQ arrays depending on the real # bands with defaults if
                // band < default size else 0 by copying default arrays over new ones
                final short[] eQPresetUserBandLevelDefault = Arrays.copyOf(
                        EQUALIZER_PRESET_USER_BAND_LEVEL_DEFAULT, mEQNumBands);
                // if no preset prefs set use CI EXTREME (= numPresets)
                final short eQPreset = (short) prefs.getInt(Key.eq_current_preset.toString(),
                        mEQNumPresets);
                final short[] bandLevel = new short[mEQNumBands];
                for (short band = 0; band < mEQNumBands; band++) {
                    if (eQPreset < mEQNumPresets) {
                        // OpenSL ES effect presets
                        bandLevel[band] = mEQPresetOpenSLESBandLevel[eQPreset][band];
                    } else {
                        // User
                        bandLevel[band] = (short) prefs.getInt(
                                Key.eq_preset_user_band_level.toString() + band,
                                eQPresetUserBandLevelDefault[band]);
                    }
                    editor.putInt(Key.eq_band_level.toString() + band, bandLevel[band]);
                    editor.putInt(Key.eq_center_freq.toString() + band, mEQCenterFreq[band]);
                    editor.putInt(Key.eq_preset_user_band_level_default.toString() + band,
                            eQPresetUserBandLevelDefault[band]);
                }
                for (short preset = 0; preset < mEQNumPresets; preset++) {
                    editor.putString(Key.eq_preset_name.toString() + preset, mEQPresetNames[preset]);
                }
                editor.commit();
            } catch (final RuntimeException e) {
                Log.e(TAG, "initEffectsPreferences: processingEnabled: " + e);
            }
        }
    }

    /**
     * Sets boolean parameter to value for given key
     *
     * @param context
     * @param key
     * @param value
     */
    public static void setParameterBoolean(final Context context, final String prefLevel, final Key key, final boolean value) {
        try {
            final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                    Context.MODE_PRIVATE);
            final ControlMode controlMode = getControlMode();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(key.toString(), value);
            editor.commit();

            if (controlMode == ControlMode.CONTROL_EFFECTS) {
                String newPrefLevel = prefLevel;
                // if current active level has changed make sure to updateDsp for the
                // now active level
                if (key.equals(Key.bluetooth) || key.equals(Key.headset)) {
                    newPrefLevel = getCurrentPrevLevel(context);
                }
                updateDsp(context, newPrefLevel);
            }
        } catch (final RuntimeException e) {
            Log.e(TAG, "setParameterBoolean: " + key + "; " + value + "; " + e);
        }
    }

    /**
     * Gets boolean parameter for given key
     *
     * @param context
     * @param key
     * @return parameter value
     */
    public static Boolean getParameterBoolean(final Context context, final String prefLevel, final Key key) {
        final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                Context.MODE_PRIVATE);
        boolean value = false;

        try {
            value = prefs.getBoolean(key.toString(), value);
        } catch (final RuntimeException e) {
            Log.e(TAG, "getParameterBoolean: " + key + "; " + value + "; " + e);
        }

        return value;

    }

    /**
     * Sets int parameter for given key and value arg0, arg1
     *
     * @param context
     * @param key
     * @param arg0
     * @param arg1
     */
    public static void setParameterInt(final Context context, final String prefLevel, final Key key, final int arg0,
            final int arg1) {
        String strKey = key.toString();
        int value = arg0;

        try {
            final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                    Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = prefs.edit();
            final ControlMode controlMode = getControlMode();

            switch (key) {
                // Equalizer
                case eq_band_level: {
                    if (arg1 == DUMMY_ARGUMENT) {
                        throw new IllegalArgumentException("Dummy arg passed.");
                    }
                    final short band = (short) arg1;
                    strKey = strKey + band;
                    editor.putInt(Key.eq_preset_user_band_level.toString() + band, value);
                    break;
                }

                case eq_current_preset: {
                    final short preset = (short) value;
                    final int numBands = prefs.getInt(Key.eq_num_bands.toString(),
                            EQUALIZER_NUMBER_BANDS_DEFAULT);
                    final int numPresets = prefs.getInt(Key.eq_num_presets.toString(),
                            EQUALIZER_NUMBER_PRESETS_DEFAULT);
                    final short[] eQPresetUserBandLevelDefault = Arrays.copyOf(
                            EQUALIZER_PRESET_USER_BAND_LEVEL_DEFAULT, numBands);
                    for (short band = 0; band < numBands; band++) {
                        short bandLevel = 0;
                        if (preset < numPresets) {
                            // OpenSL ES EQ Effect presets
                            bandLevel = mEQPresetOpenSLESBandLevel[preset][band];
                        } else {
                            // User
                            bandLevel = (short) prefs.getInt(
                                    Key.eq_preset_user_band_level.toString() + band,
                                    eQPresetUserBandLevelDefault[band]);
                        }
                        editor.putInt(Key.eq_band_level.toString() + band, bandLevel);
                    }
                    break;
                }
                case eq_preset_user_band_level:
                    // Fall through
                case eq_preset_user_band_level_default:
                    if (arg1 == DUMMY_ARGUMENT) {
                        throw new IllegalArgumentException("Dummy arg passed.");
                    }
                    final short band = (short) arg1;
                    strKey = strKey + band;
                    break;
            }

            // Set preferences
            editor.putInt(strKey, value);
            editor.apply();

            if (controlMode == ControlMode.CONTROL_EFFECTS) {
                updateDsp(context, prefLevel);
            }
        } catch (final RuntimeException e) {
            Log.e(TAG, "setParameterInt: " + key + "; " + arg0 + "; " + arg1 + "; " + e);
        }

    }

    /**
     * Sets int parameter for given key and value arg
     *
     * @param context
     * @param key
     * @param arg
     */
    public static void setParameterInt(final Context context, final String prefLevel, final Key key, final int arg) {
        setParameterInt(context, prefLevel, key, arg, DUMMY_ARGUMENT);
    }

    /**
     * Gets int parameter given key
     *
     * @param context
     * @param key
     * @return parameter value
     */
    public static int getParameterInt(final Context context, final String prefLevel, final String key) {
        int value = 0;

        try {
            final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                    Context.MODE_PRIVATE);
            value = prefs.getInt(key, value);
        } catch (final RuntimeException e) {
            Log.e(TAG, "getParameterInt: " + key + "; " + e);
        }

        return value;
    }

    /**
     * Gets int parameter given key
     *
     * @param context
     * @param key
     * @return parameter value
     */
    public static int getParameterInt(final Context context, final String prefLevel, final Key key) {
        return getParameterInt(context, prefLevel, key.toString());
    }

    /**
     * Gets int parameter given key and arg
     *
     * @param context
     * @param key
     * @param arg
     * @return parameter value
     */
    public static int getParameterInt(final Context context, final String prefLevel, final Key key, final int arg) {
        return getParameterInt(context, prefLevel, key.toString() + arg);
    }

    /**
     * Gets int parameter given key, arg0 and arg1
     *
     * @param context
     * @param key
     * @param arg0
     * @param arg1
     * @return parameter value
     */
    public static int getParameterInt(final Context context, final String prefLevel, final Key key, final int arg0,
            final int arg1) {
        return getParameterInt(context, prefLevel, key.toString() + arg0 + "_"
                + arg1);
    }

    /**
     * Gets integer array parameter given key. Returns null if not found.
     *
     * @param context
     * @param key
     * @return parameter value array
     */
    public static int[] getParameterIntArray(final Context context, final String prefLevel, final Key key) {
        final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                Context.MODE_PRIVATE);

        int[] intArray = null;
        try {
            // Get effect parameters
            switch (key) {
            case eq_level_range: {
                intArray = new int[2];
                break;
            }
            case eq_center_freq:
                // Fall through
            case eq_band_level:
                // Fall through
            case eq_preset_user_band_level:
                // Fall through
            case eq_preset_user_band_level_default:
                final int numBands = prefs.getInt(Key.eq_num_bands.toString(), 0);
                intArray = new int[numBands];
                break;
            default:
                Log.e(TAG, "getParameterIntArray: Unknown/unsupported key " + key);
                return null;
            }

            for (int i = 0; i < intArray.length; i++) {
                intArray[i] = prefs.getInt(key.toString() + i, 0);
            }

        } catch (final RuntimeException e) {
            Log.e(TAG, "getParameterIntArray: " + key + "; " + e);
        }

        return intArray;
    }

    /**
     * Gets string parameter given key. Returns empty string if not found.
     *
     * @param context
     * @param key
     * @return parameter value
     */
    public static String getParameterString(final Context context, final String prefLevel, final String key) {
        String value = "";
        try {
            final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                    Context.MODE_PRIVATE);

            // Get effect parameters
            value = prefs.getString(key, value);

        } catch (final RuntimeException e) {
            Log.e(TAG, "getParameterString: " + key + "; " + e);
        }

        return value;
    }

    /**
     * Gets string parameter given key.
     *
     * @param context
     * @param key
     * @return parameter value
     */
    public static String getParameterString(final Context context, final String prefLevel, final Key key) {
        return getParameterString(context, prefLevel, key.toString());
    }

    /**
     * Gets string parameter given key and arg.
     *
     * @param context
     * @param args
     * @return parameter value
     */
    public static String getParameterString(final Context context, final String prefLevel, final Key key, final int arg) {
        return getParameterString(context, prefLevel, key.toString() + arg);
    }

    public static ControlMode getControlMode() {
        if (mAudioSessions.size() == 0) {
            return ControlMode.CONTROL_PREFERENCES;
        }
        return ControlMode.CONTROL_EFFECTS;
    }

    public static String getCurrentPrevLevel(Context context) {
        boolean useBluetooth = ControlPanelEffect.getParameterBoolean(context,
                    ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.bluetooth);
        boolean useHeadset = ControlPanelEffect.getParameterBoolean(context,
                    ControlPanelEffect.GLOBAL_PREF_SCOPE, ControlPanelEffect.Key.headset);
        if (useBluetooth) {
            return BLUETOOTH_PREF_SCOPE;
        }
        if (useHeadset) {
            return HEADSET_PREF_SCOPE;
        }
        return SPEAKER_PREF_SCOPE;
    }

    /**
     * Opens/initializes the effects session for the given audio session with preferences linked to
     * the given package name and context.
     *
     * @param context
     * @param packageName
     * @param audioSession
     *            System wide unique audio session identifier.
     */
    public static void openSession(final Context context, final String packageName,
            final int audioSession) {
        Log.d(TAG, "openSession " + packageName + " " + audioSession);

        initEffectsPreferences(context);

        EffectSet effectSet = null; 
        if (!mAudioSessions.containsKey(audioSession)) {
            effectSet = new EffectSet(audioSession);
            mAudioSessions.put(audioSession, effectSet);
        } else {
            return;
        }

        String currentLevel = getCurrentPrevLevel(context);
        Log.d(TAG, "openSession scope = " + currentLevel);
        final SharedPreferences prefs = context.getSharedPreferences(currentLevel,
                Context.MODE_PRIVATE);

        updateEffectSet(prefs, effectSet);
    }

    /**
     * Closes the audio session (release effects) for the given session
     *
     * @param context
     * @param packageName
     * @param audioSession
     *            System wide unique audio session identifier.
     */
    public static void closeSession(final Context context, final String packageName,
            final int audioSession) {
        Log.d(TAG, "closeSession " + packageName + " " + audioSession);
        EffectSet gone = mAudioSessions.remove(audioSession);
        if (gone != null) {
            gone.release();
        }
    }

    public static void setEnabled(Context context, final String prefLevel, boolean value) {
        final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Key.global_enabled.toString(), value).commit();
        final ControlMode controlMode = getControlMode();
        if (controlMode == ControlMode.CONTROL_EFFECTS) {
            updateDsp(context, prefLevel);
        }
    }

    private static void updateEffectSet(SharedPreferences prefs, EffectSet effectSet) {
        Log.d(TAG, "updateEffectSet " + effectSet.mAudioSession);

        final boolean isGlobalEnabled = prefs.getBoolean(Key.global_enabled.toString(),
                GLOBAL_ENABLED_DEFAULT);

        final int strength = prefs.getInt(Key.virt_strength.toString(), VIRTUALIZER_STRENGTH_DEFAULT);
        effectSet.mVirtualizer.setStrength((short) strength);
        boolean virtOn = prefs.getBoolean(Key.virt_enabled.toString(), VIRTUALIZER_ENABLED_DEFAULT);
        effectSet.mVirtualizer.setEnabled(isGlobalEnabled && virtOn);

        final int bBStrength = prefs.getInt(Key.bb_strength.toString(),
                BASS_BOOST_STRENGTH_DEFAULT);
        effectSet.mBassBoost.setStrength((short) bBStrength);
        boolean bbOn = prefs.getBoolean(Key.bb_enabled.toString(), BASS_BOOST_ENABLED_DEFAULT);
        effectSet.mBassBoost.setEnabled(isGlobalEnabled && bbOn);

        final short preset = (short) prefs.getInt(Key.pr_current_preset.toString(), PRESET_REVERB_CURRENT_PRESET_DEFAULT);
        effectSet.mPresetReverb.setPreset(preset);
        boolean reverbOn = prefs.getBoolean(Key.pr_enabled.toString(), PRESET_REVERB_ENABLED_DEFAULT);
        effectSet.mPresetReverb.setEnabled(isGlobalEnabled && reverbOn);

        int eQPreset = (short) prefs .getInt(Key.eq_current_preset.toString(), mEQNumPresets);
        final int numBands = prefs.getInt(Key.eq_num_bands.toString(),
                EQUALIZER_NUMBER_BANDS_DEFAULT);
        final int numPresets = prefs.getInt(Key.eq_num_presets.toString(),
                EQUALIZER_NUMBER_PRESETS_DEFAULT);
        final short[] eQPresetUserBandLevelDefault = Arrays.copyOf(
                EQUALIZER_PRESET_USER_BAND_LEVEL_DEFAULT, numBands);

        for (short band = 0; band < numBands; band++) {
            short bandLevel = 0;
            if (eQPreset < numPresets) {
                // OpenSL ES EQ Effect presets
                bandLevel = mEQPresetOpenSLESBandLevel[eQPreset][band];
            } else {
                // User
                bandLevel = (short) prefs.getInt(
                        Key.eq_preset_user_band_level.toString() + band,
                        eQPresetUserBandLevelDefault[band]);
            }
            effectSet.mEqualizer.setBandLevel(band, bandLevel);
        }
        boolean eqOn = prefs.getBoolean(Key.eq_enabled.toString(), EQUALIZER_ENABLED_DEFAULT);
        effectSet.mEqualizer.setEnabled(isGlobalEnabled && eqOn);
    }

    private static void updateDsp(Context context, final String prefLevel) {
        final String currentLevel = getCurrentPrevLevel(context);

        if (!prefLevel.equals(currentLevel)) {
            return;
        }
        final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                Context.MODE_PRIVATE);
        Log.d(TAG, "updateDsp for level = " + prefLevel + ":" + prefs.getAll());
        for (Integer sessionId : new ArrayList<Integer>(mAudioSessions.keySet())) {
            EffectSet effectSet = mAudioSessions.get(sessionId);
            updateEffectSet(prefs, effectSet);
        }
    }

    private static void init(Context context) {
        // If EQ is not initialized already create "dummy" audio session created by
        // MediaPlayer and create effect on it to retrieve the invariable EQ properties
        if (!mIsInitialized) {
            final MediaPlayer mediaPlayer = new MediaPlayer();
            final int session = mediaPlayer.getAudioSessionId();
            Equalizer equalizerEffect = null;
            Virtualizer virtualizerEffect = null;
            try {
                equalizerEffect = new Equalizer(PRIORITY, session);

                mEQBandLevelRange = equalizerEffect.getBandLevelRange();
                mEQNumBands = equalizerEffect.getNumberOfBands();
                mEQCenterFreq = new int[mEQNumBands];
                for (short band = 0; band < mEQNumBands; band++) {
                    mEQCenterFreq[band] = equalizerEffect.getCenterFreq(band);
                }
                mEQNumPresets = equalizerEffect.getNumberOfPresets();
                mEQPresetNames = new String[mEQNumPresets];
                mEQPresetOpenSLESBandLevel = new short[mEQNumPresets][mEQNumBands];
                for (short preset = 0; preset < mEQNumPresets; preset++) {
                    mEQPresetNames[preset] = equalizerEffect.getPresetName(preset);
                    equalizerEffect.usePreset(preset);
                    for (short band = 0; band < mEQNumBands; band++) {
                        mEQPresetOpenSLESBandLevel[preset][band] = equalizerEffect
                                .getBandLevel(band);
                    }
                }

                virtualizerEffect = new Virtualizer(PRIORITY, session);
                for (String prefLevel : ALL_PREF_SCOPES) {
                    final SharedPreferences prefs = context.getSharedPreferences(prefLevel,
                            Context.MODE_PRIVATE);
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(Key.virt_strength_supported.toString(),
                            virtualizerEffect.getStrengthSupported());
                    editor.commit();
                }

                mIsInitialized = true;
            } catch (final IllegalStateException e) {
                Log.e(TAG, "Equalizer: " + e);
            } catch (final IllegalArgumentException e) {
                Log.e(TAG, "Equalizer: " + e);
            } catch (final UnsupportedOperationException e) {
                Log.e(TAG, "Equalizer: " + e);
            } catch (final RuntimeException e) {
                Log.e(TAG, "Equalizer: " + e);
            } finally {
                if (equalizerEffect != null) {
                    equalizerEffect.release();
                }
                if (virtualizerEffect != null) {
                    virtualizerEffect.release();
                }
                mediaPlayer.release();
            }
        }
    }
}
