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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.keyguard.KeyguardStatusView;
import com.android.systemui.R;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QuickQSPanel;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.tuner.TunerService;

public class QuickStatusBarHeader extends BaseStatusBarHeader implements
        NextAlarmController.NextAlarmChangeCallback, View.OnClickListener {

    private static final String TAG = "QuickStatusBarHeader";
    private ActivityStarter mActivityStarter;
    private NextAlarmController mNextAlarmController;
    private SettingsButton mSettingsButton;
    private View mSettingsContainer;
    private TextView mAlarmStatus;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mAlarmShowing;

    private ViewGroup mExpandedGroup;
    private ViewGroup mDateTimeGroup;
    private View mEmergencyOnly;
    private TextView mQsDetailHeaderTitle;
    private boolean mListening;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private QuickQSPanel mHeaderQsPanel;
    private boolean mShowEmergencyCallsOnly;
    private float mDateTimeTranslation;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private View mQsDetailHeaderBack;

    private final int[] mTmpInt2 = new int[2];

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mEmergencyOnly = findViewById(R.id.header_emergency_calls_only);
        mDateTimeTranslation = mContext.getResources().getDimension(
                R.dimen.qs_date_anim_translation);
        mDateTimeGroup = (ViewGroup) findViewById(R.id.date_time_group);
        mDateTimeGroup.findViewById(R.id.empty_time_view).setVisibility(View.GONE);
        mExpandedGroup = (ViewGroup) findViewById(R.id.expanded_group);

        mHeaderQsPanel = (QuickQSPanel) findViewById(R.id.quick_qs_panel);

        mSettingsButton = (SettingsButton) findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);

        mAlarmStatus = (TextView) findViewById(R.id.alarm_status);
        mAlarmStatus.setOnClickListener(this);

        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = (ImageView) mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) getBackground()).setForceSoftware(true);
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);

        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                setClipBounds(new Rect(getPaddingLeft(), 0, getWidth() - getPaddingRight(),
                        getHeight()));
            }
        });
    }

    @Override
    public int getCollapsedHeight() {
        return getHeight();
    }

    @Override
    public int getExpandedHeight() {
        return getHeight();
    }

    @Override
    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
        updateEverything();
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        if (nextAlarm != null) {
            mAlarmStatus.setText(KeyguardStatusView.formatNextAlarm(getContext(), nextAlarm));
        }
        mAlarmShowing = nextAlarm != null;
        updateEverything();
    }

    @Override
    public void setExpansion(float headerExpansionFraction) {
        mExpandedGroup.setAlpha(headerExpansionFraction);
        mExpandedGroup.setVisibility(headerExpansionFraction > 0 ? View.VISIBLE : View.INVISIBLE);
        mHeaderQsPanel.setAlpha(1 - headerExpansionFraction);
        mHeaderQsPanel.setVisibility(headerExpansionFraction < 1 ? View.VISIBLE : View.INVISIBLE);

        mDateTimeGroup.setTranslationY(headerExpansionFraction * mDateTimeTranslation);
        mEmergencyOnly.setAlpha(headerExpansionFraction);
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mHeaderQsPanel.setListening(listening);
        mListening = listening;
        updateListeners();
    }

    @Override
    public void updateEverything() {
        updateVisibilities();
    }

    private void updateVisibilities() {
        mAlarmStatus.setVisibility(mAlarmShowing ? View.VISIBLE : View.GONE);
        mEmergencyOnly.setVisibility(mExpanded && mShowEmergencyCallsOnly
                ? View.VISIBLE : View.INVISIBLE);
        mSettingsContainer.findViewById(R.id.tuner_icon).setVisibility(
                TunerService.isTunerEnabled(mContext) ? View.VISIBLE : View.INVISIBLE);
        mMultiUserSwitch.setVisibility(mMultiUserSwitch.hasMultipleUsers() ? View.VISIBLE
                : View.GONE);
    }

    private void updateListeners() {
        if (mListening) {
            mNextAlarmController.addStateChangedCallback(this);
        } else {
            mNextAlarmController.removeStateChangedCallback(this);
        }
    }

    @Override
    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    @Override
    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        setupHost(qsPanel.getHost());
        if (mQsPanel != null) {
            mMultiUserSwitch.setQsPanel(qsPanel);
        }
    }

    public void setupHost(final QSTileHost host) {
        host.setHeaderView(this);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host);
        mHeaderQsPanel.setMaxTiles(5);
        mHeaderQsPanel.setTiles(host.getTiles());
        host.addCallback(new QSTile.Host.Callback() {
            @Override
            public void onTilesChanged() {
                mHeaderQsPanel.setTiles(host.getTiles());
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            if (mSettingsButton.isTunerClick()) {
                if (TunerService.isTunerEnabled(mContext)) {
                    TunerService.showResetRequest(mContext, new Runnable() {
                        @Override
                        public void run() {
                            // Relaunch settings so that the tuner disappears.
                            startSettingsActivity();
                        }
                    });
                } else {
                    Toast.makeText(getContext(), R.string.tuner_toast, Toast.LENGTH_LONG).show();
                    TunerService.setTunerEnabled(mContext, true);
                }
            }
            startSettingsActivity();
        } else if (v == mAlarmStatus && mNextAlarm != null) {
            PendingIntent showIntent = mNextAlarm.getShowIntent();
            if (showIntent != null && showIntent.isActivity()) {
                mActivityStarter.startActivity(showIntent.getIntent(), true /* dismissShade */);
            }
        }
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    @Override
    public void setNextAlarmController(NextAlarmController nextAlarmController) {
        mNextAlarmController = nextAlarmController;
    }

    @Override
    public void setBatteryController(BatteryController batteryController) {
        // Don't care
    }

    @Override
    public void setUserInfoController(UserInfoController userInfoController) {
        userInfoController.addListener(new UserInfoController.OnUserInfoChangedListener() {
            @Override
            public void onUserInfoChanged(String name, Drawable picture) {
                mMultiUserAvatar.setImageDrawable(picture);
            }
        });
    }

    @Override
    public void setEmergencyCallsOnly(boolean show) {
        boolean changed = show != mShowEmergencyCallsOnly;
        if (changed) {
            mShowEmergencyCallsOnly = show;
            if (mExpanded) {
                updateEverything();
            }
        }
    }
}
