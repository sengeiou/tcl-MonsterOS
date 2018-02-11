/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.settings;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.camera.app.CameraApp;
import com.android.camera.debug.Log;
import com.tct.camera.R;

/**
 * This class allows Settings UIs to display and set boolean values controlled
 * via the {@link SettingsManager}. The Default {@link SwitchPreference} uses
 * {@link android.content.SharedPreferences} as a backing store; since the
 * {@link SettingsManager} stores all settings as Strings we need to ensure we
 * get and set boolean settings through the manager.
 */
public class SwitchPreferenceSingle extends SwitchPreference implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final Log.Tag TAG = new Log.Tag("SwitchPreferenceSingle");
    private Switch mSwitchPreferenceSwitch;

    public interface onSwitchChangeListener {
        void onSwitchChanged(SwitchPreferenceSingle preference);
    }

    private onSwitchChangeListener mOnSwitchChangeListener;

    public SwitchPreferenceSingle(Context context) {
        this(context,null);
    }

    public SwitchPreferenceSingle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchPreferenceSingle(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean getPersistedBoolean(boolean defaultReturnValue) {
        CameraApp cameraApp = getCameraApp();
        if (cameraApp == null) {
            return defaultReturnValue;
        }
        SettingsManager settingsManager = cameraApp.getSettingsManager();
        if (settingsManager != null) {
            return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL, getKey());
        } else {
            return defaultReturnValue;
        }
    }

    @Override
    public boolean persistBoolean(boolean value) {
        CameraApp cameraApp = getCameraApp();
        if (cameraApp == null) {
            return false;
        }
        SettingsManager settingsManager = cameraApp.getSettingsManager();
        if (settingsManager != null) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, getKey(), value);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        super.onCreateView(parent);
        return LayoutInflater.from(getContext()).inflate(R.layout.switch_preference,
                    parent, false);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        RelativeLayout switchPreferenceLayout = (RelativeLayout) view.findViewById(R.id.switch_preference_single);
        TextView switchPreferenceTitle = (TextView) view.findViewById(R.id.switch_preference_single_title);
        mSwitchPreferenceSwitch = (Switch) view.findViewById(R.id.switch_preference_single_switch);
        View switchPreferenceLine = view.findViewById(R.id.switch_preference_single_line);
//        when click  switchPreferenceLayout don't trigger persistBoolean which save value in shared preference
//        switchPreferenceLayout.setOnClickListener(this);
        switchPreferenceTitle.setText(getTitle());
        if (mSwitchPreferenceSwitch != null) {
            // this must be call, otherwise it can't be save in sharedPreference
            mSwitchPreferenceSwitch.setOnCheckedChangeListener(null);
            mSwitchPreferenceSwitch.setChecked(getPersistedBoolean(false));
            mSwitchPreferenceSwitch.setOnCheckedChangeListener(this);
        }

        if (getKey().equals(Keys.KEY_RECORD_LOCATION)) {
            switchPreferenceLine.setVisibility(View.GONE);
        }
        if (getKey().equals(Keys.KEY_CAMERA_HDR)) {
            switchPreferenceLine.setVisibility(View.VISIBLE);
        }
    }

    private CameraApp getCameraApp() {
        Context context = getContext();
        if (context instanceof Activity) {
            Application application = ((Activity) context).getApplication();
            if (application instanceof CameraApp) {
                return (CameraApp) application;
            }
        }
        return null;
    }

    /**
     * Called when the checked state of Switch has changed.
     *
     * @param buttonView The Switch view whose state has changed.
     * @param isChecked  The new checked state of Switch.
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SwitchPreferenceSingle.this.setChecked(isChecked);
        if (mOnSwitchChangeListener != null) {
            mOnSwitchChangeListener.onSwitchChanged(this);
        }
    }

    /**
     * Called when {@link SwitchPreferenceSummary#switchPreferenceLayout} has been clicked.
     *
     * @param v The {@link SwitchPreferenceSummary#switchPreferenceLayout} that was clicked.
     */
    @Override
    public void onClick(View v) {
        final boolean newValue = !mSwitchPreferenceSwitch.isChecked();
        mSwitchPreferenceSwitch.setChecked(newValue);
    }

    public void setSwitchChangeListener(onSwitchChangeListener l) {
        mOnSwitchChangeListener = l;
    }
}
