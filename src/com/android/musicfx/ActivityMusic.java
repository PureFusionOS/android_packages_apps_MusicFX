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

import com.android.audiofx.OpenSLESConstants;
import com.android.musicfx.widget.Gallery;
import com.android.musicfx.widget.InterceptableLinearLayout;
import com.android.musicfx.widget.Knob;
import com.android.musicfx.widget.Knob.OnKnobChangeListener;
import com.android.musicfx.widget.Visualizer;
import com.android.musicfx.widget.Visualizer.OnSeekBarChangeListener;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.os.SystemProperties;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.NavigationView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import android.util.DisplayMetrics;

import java.util.Formatter;
import java.util.Locale;
import java.util.UUID;

/**
 *
 */
public class ActivityMusic extends AppCompatActivity {
    private final static String TAG = "MusicFXActivityMusic";

    /**
     * Max number of EQ bands supported
     */
    private final static int EQUALIZER_MAX_BANDS = 32;

    /**
     * Max levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MAX_LEVEL = 1000;

    /**
     * Min levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MIN_LEVEL = -1000;

    /**
     * Indicates if Virtualizer effect is supported.
     */
    private boolean mVirtualizerSupported;
    private boolean mVirtualizerIsHeadphoneOnly;
    /**
     * Indicates if BassBoost effect is supported.
     */
    private boolean mBassBoostSupported;
    /**
     * Indicates if Equalizer effect is supported.
     */
    private boolean mEqualizerSupported;
    /**
     * Indicates if Preset Reverb effect is supported.
     */
    private boolean mPresetReverbSupported;

    // Equalizer fields
    private final Visualizer[] mEqualizerVisualizer = new Visualizer[EQUALIZER_MAX_BANDS];
    private int mNumberEqualizerBands;
    private int mEqualizerMinBandLevel;
    private int mEQPresetUserPos = 1;
    private int mEQPreset;
    private int[] mEQPresetUserBandLevelsPrev;
    private String[] mEQPresetNames;
    private String[] mReverbPresetNames;

    private int mPRPreset;
    private int mPRPresetPrevious;

    private boolean mIsHeadsetOn = false;
    private boolean mIsSpeakerOn = false;
    private boolean mIsBluetoothOn = false;

    private TextView mCurrentLevelText;
    private StringBuilder mFormatBuilder = new StringBuilder();
    private Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

    private String mCurrentLevel = ControlPanelEffect.SPEAKER_PREF_SCOPE;
    private NavigationView mDrawerList;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Switch mToolbarSwitch;
    private ViewGroup mViewGroup;
    private Gallery mGallery;

    /**
     * Array containing RSid of preset reverb names.
     */
    private static final int[] mReverbPresetRSids = {
        R.string.none, R.string.smallroom, R.string.mediumroom, R.string.largeroom,
        R.string.mediumhall, R.string.largehall, R.string.plate
    };

    /**
     * Context field
     */
    private Context mContext;

    private final BroadcastReceiver mPrefLevelChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ControlPanelEffect.PREF_SCOPE_CHANGED)) {
                Log.i(TAG, "onReceive " + action);
                if ((mVirtualizerSupported) || (mBassBoostSupported) || (mEqualizerSupported)
                        || (mPresetReverbSupported)) {
                    String currentLevel = ControlPanelEffect.getCurrentPrevLevel(ActivityMusic.this);
                    updateCurrentLevelInfo(currentLevel);
                }
            }
        }
    };

    /*
     * Declares and initializes all objects and widgets in the layouts and the CheckBox and SeekBar
     * onchange methods on creation.
     *
     * (non-Javadoc)
     *
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init context to be used in listeners
        mContext = this;
        mCurrentLevel = ControlPanelEffect.getCurrentPrevLevel(this);
        Log.d(TAG, "onCreate " + mCurrentLevel);

        ControlPanelEffect.initEffectsPreferences(mContext);

        // query available effects
        final Descriptor[] effects = AudioEffect.queryEffects();

        // Determine available/supported effects
        Log.d(TAG, "Available effects:");
        for (final Descriptor effect : effects) {
            Log.d(TAG, effect.name.toString() + ", type: " + effect.type.toString());

            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
                mVirtualizerIsHeadphoneOnly = !isVirtualizerTransauralSupported();
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                mEqualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_PRESET_REVERB)) {
                mPresetReverbSupported = true;
            }
        }

        setContentView(R.layout.music_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mViewGroup = (ViewGroup) findViewById(R.id.contentSoundEffects);

        // Fill array with presets from AudioEffects call.
        // allocate a space for 1 extra strings (User)
        final int numPresets = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_num_presets);
        mEQPresetNames = new String[numPresets + 1];
        for (short i = 0; i < numPresets; i++) {
            final String eqPresetName = ControlPanelEffect.getParameterString(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.eq_preset_name, i);
            mEQPresetNames[i] = localizePresetName(eqPresetName);
        }
        mEQPresetNames[numPresets] = getString(R.string.user);
        mEQPresetUserPos = numPresets;

        // Load string resource of reverb presets
        mReverbPresetNames = new String[mReverbPresetRSids.length];
        for (short i = 0; i < mReverbPresetRSids.length; ++i) {
            mReverbPresetNames[i] = getString(mReverbPresetRSids[i]);
        }
        mCurrentLevelText = (TextView)findViewById(R.id.switchstatus);
        mCurrentLevelText.setCompoundDrawableTintList(new ColorStateList(new int[][] { new int[0] },
                new int[] { getResources().getColor(R.color.current_out_source_color) }));

        // Watch for button clicks and initialization.
        if ((mVirtualizerSupported) || (mBassBoostSupported) || (mEqualizerSupported)
                || (mPresetReverbSupported)) {
            // Initialize the Virtualizer elements.
            // Set the SeekBar listener.
            if (mVirtualizerSupported) {
                final Knob knob = (Knob) findViewById(R.id.vIStrengthKnob);
                knob.setMax(OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH -
                        OpenSLESConstants.VIRTUALIZER_MIN_STRENGTH);
                knob.setOnKnobChangeListener(new OnKnobChangeListener() {
                    // Update the parameters while Knob changes and set the
                    // effect parameter.
                    @Override
                    public void onValueChanged(final Knob knob, final int value,
                        final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.virt_strength, value);
                    }

                    @Override
                    public boolean onSwitchChanged(final Knob knob, boolean on) {
                        if (on && (!mIsHeadsetOn && !mIsBluetoothOn && mVirtualizerIsHeadphoneOnly)) {
                            showHeadsetMsg(getString(R.string.headset_plug));
                            return false;
                        }
                        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.virt_enabled, on);
                        return true;
                    }
                });
            }

            // Initialize the Bass Boost elements.
            // Set the SeekBar listener.
            if (mBassBoostSupported) {
                final Knob knob = (Knob) findViewById(R.id.bBStrengthKnob);
                knob.setMax(OpenSLESConstants.BASSBOOST_MAX_STRENGTH
                        - OpenSLESConstants.BASSBOOST_MIN_STRENGTH);
                knob.setOnKnobChangeListener(new OnKnobChangeListener() {
                    // Update the parameters while SeekBar changes and set the
                    // effect parameter.

                    @Override
                    public void onValueChanged(final Knob knob, final int value,
                            final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.bb_strength, value);
                    }

                    @Override
                    public boolean onSwitchChanged(final Knob knob,boolean on) {
                        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.bb_enabled, on);
                        return true;
                    }
                });
            }

            mGallery = (Gallery) findViewById(R.id.eqPresets);
            // Initialize the Equalizer elements.
            if (mEqualizerSupported) {
                mEQPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.eq_current_preset);
                if (mEQPreset >= mEQPresetNames.length) {
                    mEQPreset = 0;
                }
                equalizerPresetsInit();
                equalizerBandsInit((LinearLayout)findViewById(R.id.eqcontainer));
            }

            // Initialize the Preset Reverb elements.
            // Set Spinner listeners.
            if (mPresetReverbSupported) {
                mPRPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.pr_current_preset);
                mPRPresetPrevious = mPRPreset;
                reverbSpinnerInit((Spinner)findViewById(R.id.prSpinner));
            }

        } else {
            mViewGroup.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.noEffectsTextView)).setVisibility(View.VISIBLE);
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (NavigationView) findViewById(R.id.left_drawer);

        // Set the list's click listener
        mDrawerList.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                mDrawerLayout.closeDrawer(mDrawerList);
                if (id == R.id.menu_speaker) {
                    updateForLevel(ControlPanelEffect.SPEAKER_PREF_SCOPE);
                } else if (id == R.id.menu_headset) {
                    updateForLevel(ControlPanelEffect.HEADSET_PREF_SCOPE);
                } else if (id == R.id.menu_bluetooth) {
                    updateForLevel(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);
                }
                return true;
            }
        });
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();
    }

    @Override
    public void setTitle(CharSequence title) {
        getSupportActionBar().setTitle(title);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void updateForLevel(String level) {
        if (!level.equals(mCurrentLevel)) {
            mCurrentLevel = level;
            mIsHeadsetOn = mCurrentLevel.equals(ControlPanelEffect.HEADSET_PREF_SCOPE);
            mIsSpeakerOn = mCurrentLevel.equals(ControlPanelEffect.SPEAKER_PREF_SCOPE);
            mIsBluetoothOn = mCurrentLevel.equals(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);
            updateUI();
            updateTitle();
        }
    }

    private void updateTitle() {
        if (mCurrentLevel == ControlPanelEffect.SPEAKER_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_speaker));
            mDrawerList.getMenu().findItem(R.id.menu_speaker).setChecked(true);
        } else if (mCurrentLevel == ControlPanelEffect.HEADSET_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_headset));
            mDrawerList.getMenu().findItem(R.id.menu_headset).setChecked(true);
        } else if (mCurrentLevel == ControlPanelEffect.BLUETOOTH_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_bluetooth));
            mDrawerList.getMenu().findItem(R.id.menu_bluetooth).setChecked(true);
        }
    }

    private void updateCurrentLevelInfo(String level) {
        if (level == ControlPanelEffect.SPEAKER_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_speaker));
            mCurrentLevelText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_speaker, 0, 0, 0);
        } else if (level == ControlPanelEffect.HEADSET_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_headset));
            mCurrentLevelText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_headphones, 0, 0, 0);
        } else if (level == ControlPanelEffect.BLUETOOTH_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_bluetooth));
            mCurrentLevelText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth, 0, 0, 0);
        }
    }

    private final String localizePresetName(final String name) {
        final String[] names = {
            "Normal", "Classical", "Dance", "Flat", "Folk",
            "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"
        };
        final int[] ids = {
            R.string.normal, R.string.classical, R.string.dance, R.string.flat, R.string.folk,
            R.string.heavy_metal, R.string.hip_hop, R.string.jazz, R.string.pop, R.string.rock
        };

        for (int i = names.length - 1; i >= 0; --i) {
            if (names[i].equals(name)) {
                return getString(ids[i]);
            }
        }
        return name;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        mToolbarSwitch = (Switch) menu.findItem(R.id.toolbar_switch).getActionView().findViewById(R.id.toolbar_switch_button);
        mToolbarSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // set parameter and state
                ControlPanelEffect.setEnabled(mContext, mCurrentLevel, isChecked);
                // Enable Linear layout (in scroll layout) view with all
                // effect contents depending on checked state
                setEnabledAllChildren(mViewGroup, isChecked);
                // update UI according to headset state
                updateUIHeadset(false);
                setInterception(isChecked);
            }
        });
        updateUI();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (id == R.id.toolbar_switch) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * (non-Javadoc)
     *            // Update UI

     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if ((mVirtualizerSupported) || (mBassBoostSupported) || (mEqualizerSupported)
                || (mPresetReverbSupported)) {

            if (!isServiceRunning()) {
                if (!SystemProperties.getBoolean("ro.musicfx.disabled", false)) {
                    Log.d(TAG, "starting SystemService from onResume");
                    startService(new Intent(this, SystemService.class));
                }
            }
            mCurrentLevel = ControlPanelEffect.getCurrentPrevLevel(this);
            mIsHeadsetOn = mCurrentLevel.equals(ControlPanelEffect.HEADSET_PREF_SCOPE);
            mIsSpeakerOn = mCurrentLevel.equals(ControlPanelEffect.SPEAKER_PREF_SCOPE);
            mIsBluetoothOn = mCurrentLevel.equals(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);

            updateUI();
            updateTitle();
            updateCurrentLevelInfo(mCurrentLevel);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ControlPanelEffect.PREF_SCOPE_CHANGED);
            registerReceiver(mPrefLevelChanged, intentFilter);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mPrefLevelChanged);
        } catch (Exception e) {
        }
    }

    private void reverbSpinnerInit(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                R.layout.spinner_item, mReverbPresetNames);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mPRPresetPrevious) {
                    presetReverbSetPreset(position);
                }
                mPRPresetPrevious = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(mPRPreset);
        spinner.setBackgroundResource(R.drawable.rev_spinner);
    }

    private void equalizerPresetsInit() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.equalizer_presets,
                mEQPresetNames);

        mGallery.setAdapter(adapter);
        mGallery.setOnItemSelectedListener(new Gallery.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                mEQPreset = position;
                showSeekBar(position == mEQPresetUserPos);
                equalizerSetPreset(position);
            }
        });
        mGallery.setSelection(mEQPreset);
    }


    /**
     * En/disables all children for a given view. For linear and relative layout children do this
     * recursively
     *
     * @param viewGroup
     * @param enabled
     */
    private void setEnabledAllChildren(final ViewGroup viewGroup, final boolean enabled) {
        final int count = viewGroup.getChildCount();
        final View bb = findViewById(R.id.bBStrengthKnob);
        final View virt = findViewById(R.id.vIStrengthKnob);
        final View eq = findViewById(R.id.eqcontainer);
        boolean on = true;

        for (int i = 0; i < count; i++) {
            final View view = viewGroup.getChildAt(i);
            if ((view instanceof LinearLayout) || (view instanceof RelativeLayout)) {
                final ViewGroup vg = (ViewGroup) view;
                setEnabledAllChildren(vg, enabled);
            }

            if (enabled && view == virt) {
                on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.virt_enabled);
                view.setEnabled(on);
            } else if (enabled && view == bb) {
                on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.bb_enabled);
                view.setEnabled(on);
            } else if (enabled && view == eq) {
                showSeekBar(mEQPreset == mEQPresetUserPos);
                view.setEnabled(true);
            } else {
                view.setEnabled(enabled);
            }
        }
    }

    /**
     * Updates UI (checkbox, seekbars, enabled states) according to the current stored preferences.
     */
    private void updateUI() {
        if (mToolbarSwitch == null) {
            return;
        }
        final boolean isEnabled = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                ControlPanelEffect.Key.global_enabled);
        //mToggleSwitch.setChecked(isEnabled);
        //mCurrentLevelText.setText(isEnabled? R.string.toggle_button_on : R.string.toggle_button_off);
        mToolbarSwitch.setChecked(isEnabled);
        setEnabledAllChildren((ViewGroup) findViewById(R.id.contentSoundEffects), isEnabled);
        updateUIHeadset(false);

        if (mVirtualizerSupported) {
            Knob knob = (Knob) findViewById(R.id.vIStrengthKnob);
            int strength = ControlPanelEffect
                    .getParameterInt(mContext, mCurrentLevel,
                            ControlPanelEffect.Key.virt_strength);
            knob.setValue(strength);
            boolean hasStrength = ControlPanelEffect.getParameterBoolean(mContext,
                    mCurrentLevel, ControlPanelEffect.Key.virt_strength_supported);
            if (!hasStrength) {
                knob.setVisibility(View.GONE);
            }
        }
        if (mBassBoostSupported) {
            ((Knob) findViewById(R.id.bBStrengthKnob)).setValue(ControlPanelEffect
                    .getParameterInt(mContext, mCurrentLevel,
                            ControlPanelEffect.Key.bb_strength));
        }
        if (mEqualizerSupported) {
            equalizerUpdateDisplay();
        }
        if (mPresetReverbSupported) {
            mPRPreset = ControlPanelEffect.getParameterInt(
                                    mContext, mCurrentLevel,
                                    ControlPanelEffect.Key.pr_current_preset);
            ((Spinner)findViewById(R.id.prSpinner)).setSelection(mPRPreset);
        }

        setInterception(isEnabled);
    }

    private void setInterception(boolean isEnabled) {
        final InterceptableLinearLayout ill =
            (InterceptableLinearLayout) findViewById(R.id.contentSoundEffects);
        ill.setInterception(!isEnabled);
        if (isEnabled) {
            ill.setOnClickListener(null);
        } else {
            ill.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Toast toast = Toast.makeText(mContext,
                        getString(R.string.power_on_prompt), Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            });
        }
        ill.setBackgroundResource(R.drawable.knobs_area);
    }

    /**
     * Updates UI for headset mode. En/disable VI and BB controls depending on
     * headset state (on/off) if effects are on. Do the inverse for their
     * layouts so they can take over control/events.
     */
    private void updateUIHeadset(boolean force) {
        final Knob bBKnob = (Knob) findViewById(R.id.bBStrengthKnob);
        //bBKnob.setBinary(mIsSpeakerOn);
        bBKnob.setEnabled(mToolbarSwitch.isChecked());
        final Knob vIKnob = (Knob) findViewById(R.id.vIStrengthKnob);
        vIKnob.setEnabled(mToolbarSwitch.isChecked());

        if (!force) {
            boolean on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.bb_enabled);
            bBKnob.setOn(mToolbarSwitch.isChecked() && on);
            on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.virt_enabled);
            vIKnob.setOn(mToolbarSwitch.isChecked() && on);
        }
    }

    /**
     * Initializes the equalizer elements. Set the SeekBars and Spinner listeners.
     */
    private void equalizerBandsInit(LinearLayout eqcontainer) {
        // Initialize the N-Band Equalizer elements.
        mNumberEqualizerBands = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_num_bands);
        mEQPresetUserBandLevelsPrev = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_preset_user_band_level);
        final int[] centerFreqs = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_center_freq);
        final int[] bandLevelRange = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_level_range);
        mEqualizerMinBandLevel = (int) Math.min(EQUALIZER_MIN_LEVEL, bandLevelRange[0]);
        final int mEqualizerMaxBandLevel = (int) Math.max(EQUALIZER_MAX_LEVEL, bandLevelRange[1]);
        final OnSeekBarChangeListener listener = new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final Visualizer v, final int progress,
                    final boolean fromUser) {
                for (short band = 0; band < mNumberEqualizerBands; ++band) {
                    if (mEqualizerVisualizer[band] == v) {
                        final short level = (short) (progress + mEqualizerMinBandLevel);
                        if (fromUser) {
                            equalizerBandUpdate(band, level);
                        }
                        break;
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(final Visualizer v) {
            }

            @Override
            public void onStopTrackingTouch(final Visualizer v) {
            }
        };

        final OnTouchListener tl = new OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mEQPreset != mEQPresetUserPos) {
                            final Toast toast = Toast.makeText(mContext,
                                    getString(R.string.eq_custom), Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.TOP | Gravity.CENTER, 0,
                                    toast.getYOffset() * 2);
                            toast.show();
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        };

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int pixels = getResources().getDimensionPixelOffset(R.dimen.each_visualizer_width);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                pixels, ViewGroup.LayoutParams.MATCH_PARENT);
        for (int band = 0; band < mNumberEqualizerBands; band++) {
            // Unit conversion from mHz to Hz and use k prefix if necessary to display
            final int centerFreq = centerFreqs[band] / 1000;
            float centerFreqHz = centerFreq;
            String unitPrefix = "";
            if (centerFreqHz >= 1000) {
                centerFreqHz = centerFreqHz / 1000;
                unitPrefix = "k";
            }

            final Visualizer v = new Visualizer(mContext);
            v.setText(format("%.0f", centerFreqHz) + unitPrefix);
            v.setMax(mEqualizerMaxBandLevel - mEqualizerMinBandLevel);
            v.setOnSeekBarChangeListener(listener);
            v.setOnTouchListener(tl);
            eqcontainer.addView(v, lp);
            mEqualizerVisualizer[band] = v;
        }

        TextView tv = (TextView) findViewById(R.id.maxLevelText);
        tv.setText(String.format("+%d dB", (int) Math.ceil(mEqualizerMaxBandLevel / 100)));
        tv = (TextView) findViewById(R.id.centerLevelText);
        tv.setText("0 dB");
        tv = (TextView) findViewById(R.id.minLevelText);
        tv.setText(String.format("%d dB", (int) Math.floor(mEqualizerMinBandLevel / 100)));
        equalizerUpdateDisplay();
    }

    private String format(String format, Object... args) {
        mFormatBuilder.setLength(0);
        mFormatter.format(format, args);
        return mFormatBuilder.toString();
    }

    private void showSeekBar(boolean show) {
        for (int i = 0; i < mNumberEqualizerBands; ++i) {
            mEqualizerVisualizer[i].setShowSeekBar(show);
        }
    }

    /**
     * Updates the EQ by getting the parameters.
     */
    private void equalizerUpdateDisplay() {
        // Update and show the active N-Band Equalizer bands.
        final int[] bandLevels = ControlPanelEffect.getParameterIntArray(mContext,
                mCurrentLevel, ControlPanelEffect.Key.eq_band_level);
        for (short band = 0; band < mNumberEqualizerBands; band++) {
            final int level = bandLevels[band];
            final int progress = level - mEqualizerMinBandLevel;
            mEqualizerVisualizer[band].setProgress(progress);
        }
        mEQPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_current_preset);
        if (mEQPreset >= mEQPresetNames.length) {
            mEQPreset = 0;
        }
        mGallery.setSelection(mEQPreset);
    }

    /**
     * Updates/sets a given EQ band level.
     *
     * @param band
     *            Band id
     * @param level
     *            EQ band level
     */
    private void equalizerBandUpdate(final int band, final int level) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_band_level, level, band);
    }

    /**
     * Sets the given EQ preset.
     *
     * @param preset
     *            EQ preset id.
     */
    private void equalizerSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_current_preset, preset);
        equalizerUpdateDisplay();
    }

    /**
     * Sets the given PR preset.
     *
     * @param preset
     *            PR preset id.
     */
    private void presetReverbSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.pr_current_preset, preset);
    }

    /**
     * Show msg that headset needs to be plugged.
     */
    private void showHeadsetMsg(String message) {
        final Context context = getApplicationContext();
        final int duration = Toast.LENGTH_SHORT;

        final Toast toast = Toast.makeText(context, message, duration);
        toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
        toast.show();
    }

    private boolean isServiceRunning() {
        final ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(getPackageName() + ".SystemService")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVirtualizerTransauralSupported() {
        Virtualizer virt = null;
        boolean transauralSupported = false;
        try {
            virt = new Virtualizer(0, android.media.AudioSystem.newAudioSessionId());
            transauralSupported = virt.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO,
                    Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL);
        } catch (Exception e) {
        } finally {
            if (virt != null) {
                virt.release();
            }
        }
        return transauralSupported;
    }
}
