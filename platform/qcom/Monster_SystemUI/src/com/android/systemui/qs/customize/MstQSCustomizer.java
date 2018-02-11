/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.qs.customize;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto;
import com.android.systemui.R;
import com.android.systemui.qs.QSContainer;
import com.android.systemui.qs.QSDetailClipper;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.policy.KeyguardMonitor.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows full-screen customization of QS, through show() and hide().
 *
 * This adds itself to the status bar window, so it can appear on top of quick settings and
 * *someday* do fancy animations to get into/out of it.
 */

/**
 * Created by kth on 16-9-27.
 * TCL Monster: kth add for update QS edit layout
 */
public class MstQSCustomizer extends LinearLayout implements OnMenuItemClickListener {

    private static final int MENU_RESET = Menu.FIRST;


    private boolean isShown;
    private QSTileHost mHost;
    private RecyclerView mRecyclerView;
    private MstTileAdapter mTileAdapter;
    private boolean mCustomizing;
    private NotificationsQuickSettingsContainer mNotifQsContainer;
    private QSContainer mQsContainer;

    public MstQSCustomizer(Context context, AttributeSet attrs) {
        super(new ContextThemeWrapper(context, R.style.edit_theme), attrs);
        //super(context, attrs);

        LayoutInflater.from(getContext()).inflate(R.layout.mst_qs_customize_panel_content, this);

        TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.homeAsUpIndicator, value, true);

        mRecyclerView = (RecyclerView) findViewById(android.R.id.list);
        mTileAdapter = new MstTileAdapter(getContext());
        mRecyclerView.setAdapter(mTileAdapter);
        mTileAdapter.getItemTouchHelper().attachToRecyclerView(mRecyclerView);
        GridLayoutManager layout = new GridLayoutManager(getContext(), 3);
        layout.setSpanSizeLookup(mTileAdapter.getSizeLookup());
        mRecyclerView.setLayoutManager(layout);
        mRecyclerView.addItemDecoration(mTileAdapter.getItemDecoration());
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setMoveDuration(TileAdapter.MOVE_DURATION);
        mRecyclerView.setItemAnimator(animator);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View navBackdrop = findViewById(R.id.nav_bar_background);
        if (navBackdrop != null) {
            boolean shouldShow = newConfig.smallestScreenWidthDp >= 600
                    || newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE;
            navBackdrop.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
    }


    public void setHost(QSTileHost host) {
        mHost = host;
        mTileAdapter.setHost(host);
    }

    public void setContainer(NotificationsQuickSettingsContainer notificationsQsContainer) {
        mNotifQsContainer = notificationsQsContainer;
    }

    public void setQsContainer(QSContainer qsContainer) {
        mQsContainer = qsContainer;
    }

    public void show(int x, int y) {
        if (!isShown) {
            MetricsLogger.visible(getContext(), MetricsProto.MetricsEvent.QS_EDIT);
            isShown = true;
            setTileSpecs();
            setVisibility(View.VISIBLE);
//            mClipper.animateCircularClip(x, y, true, mExpandAnimationListener);
            new TileQueryHelper(mContext, mHost).setListener(mTileAdapter);
            announceForAccessibility(mContext.getString(
                    R.string.accessibility_desc_quick_settings_edit));
//            mHost.getKeyguardMonitor().addCallback(mKeyguardCallback);
        }
    }

    public void hide(int x, int y) {
        if (isShown) {
            MetricsLogger.hidden(getContext(), MetricsProto.MetricsEvent.QS_EDIT);
            isShown = false;
            setCustomizing(false);
            save();
//            mClipper.animateCircularClip(x, y, false, mCollapseAnimationListener);
            announceForAccessibility(mContext.getString(
                    R.string.accessibility_desc_quick_settings));
//            mHost.getKeyguardMonitor().removeCallback(mKeyguardCallback);
        }
    }

    private void setCustomizing(boolean customizing) {
        mCustomizing = customizing;
    }

    public boolean isCustomizing() {
        return mCustomizing;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                break;
        }
        return false;
    }

    public void reset() {
        ArrayList<String> tiles = new ArrayList<>();
        String defTiles = mContext.getString(R.string.mst_quick_settings_tiles_default);//modify by chenhl
        for (String tile : defTiles.split(",")) {
            tiles.add(tile);
        }
        mTileAdapter.setTileSpecs(tiles);
    }

    private void setTileSpecs() {
        List<String> specs = new ArrayList<>();
        for (QSTile tile : mHost.getTiles()) {
            specs.add(tile.getTileSpec());
        }
        mTileAdapter.setTileSpecs(specs);
        mRecyclerView.setAdapter(mTileAdapter);
    }

    private void save() {
        mTileAdapter.saveSpecs(mHost);
    }

    private final Callback mKeyguardCallback = new Callback() {
        @Override
        public void onKeyguardChanged() {
            if (mHost.getKeyguardMonitor().isShowing()) {
                hide(0, 0);
            }
        }
    };

    private final AnimatorListener mExpandAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            setCustomizing(true);
            mNotifQsContainer.setCustomizerAnimating(false);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mNotifQsContainer.setCustomizerAnimating(false);
        }
    };

    private final AnimatorListener mCollapseAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (!isShown) {
                setVisibility(View.GONE);
            }
            mNotifQsContainer.setCustomizerAnimating(false);
            mRecyclerView.setAdapter(mTileAdapter);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            if (!isShown) {
                setVisibility(View.GONE);
            }
            mNotifQsContainer.setCustomizerAnimating(false);
        }
    };
}