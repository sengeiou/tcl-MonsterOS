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
 * limitations under the License
 */

package com.android.dialer.settings;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import mst.preference.RingtonePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.dialer.R;

/**
 * RingtonePreference which doesn't show default ringtone setting.
 */
public class DefaultRingtonePreference extends RingtonePreference {
    private TextView mTextView;
    private ImageView mImageView;
    private String mDetail;
    private boolean mShow = false;
    
    public DefaultRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.tct_pref_widget);
    }

    @Override
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        super.onPrepareRingtonePickerIntent(ringtonePickerIntent);

        /*
         * Since this preference is for choosing the default ringtone, it
         * doesn't make sense to show a 'Default' item.
         */
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
    }

    @Override
    protected void onSaveRingtone(Uri ringtoneUri) {
        if (!Settings.System.canWrite(getContext())) {
            Toast.makeText(
                    getContext(),
                    getContext().getResources().getString(R.string.toast_cannot_write_system_settings),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
    }

    @Override
    protected Uri onRestoreRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
    }

    /**
     * M: In some edge cases such as pin lock, the MediaProvider is not loaded by PMS
     * so may occur ActivityNotFoundException, if this happens,just Toast and ignore
     */
    @Override
    protected void onClick() {
        try {
            super.onClick();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.activity_not_available,
                   Toast.LENGTH_SHORT).show();
        }
    }
    

    @Override
    public void onBindView(View view) {
        super.onBindView(view);

        View widgetFrame = view.findViewById(com.android.internal.R.id.widget_frame);

        if (widgetFrame != null) {
            mTextView = (TextView) widgetFrame.findViewById(R.id.pref_tv_detail);
            if (!TextUtils.isEmpty(mDetail)) {
                mTextView.setText(mDetail);
            } else {
                mTextView.setText("");
            }
            mImageView = (ImageView) widgetFrame.findViewById(R.id.pref_image_detail);
            if (mShow) {
                mImageView.setVisibility(View.GONE);
            }
        }
    }

    public void setDetail(String detail) {
        this.mDetail = detail;
        notifyChanged();
    }
}