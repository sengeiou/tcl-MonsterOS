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
package com.android.systemui.tcl;

import android.app.INotificationManager;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.util.Log;


public class NotificationBackend {
    private static final String TAG = "NotificationBackend";

    static INotificationManager sINM = INotificationManager.Stub.asInterface(
            ServiceManager.getService(Context.NOTIFICATION_SERVICE));

    public AppRow loadAppRow(Context context, PackageManager pm, ApplicationInfo app) {
        final AppRow row = new AppRow();
        row.pkg = app.packageName;
        row.uid = app.uid;
        try {
//            row.label = app.loadLabel(pm);
            row.label = Utils.getApplicationLabelAsUser(context,app.packageName, UserHandle.getUserId(app.uid));
        } catch (Throwable t) {
            Log.e(TAG, "Error loading application label for " + row.pkg, t);
            row.label = row.pkg;
        }
        //row.icon = app.loadIcon(pm);
        row.banned = getNotificationsBanned(row.pkg, row.uid);
        //row.appImportance = getImportance(row.pkg, row.uid);
        //row.appBypassDnd = getBypassZenMode(row.pkg, row.uid);
        //row.appVisOverride = getVisibilityOverride(row.pkg, row.uid);
        //row.lockScreenSecure = new LockPatternUtils(context).isSecure(
        //        UserHandle.myUserId());
        // begin add by zhicang.liu
        row.sensitive = getSensitive(row.pkg, row.uid);
        row.superscript = getSuperScript(row.pkg, row.uid);
        row.lockscreennotify = getLockScreenNotify(row.pkg, row.uid);
        // end add by zhicang.liu
        return row;
    }

    public AppRow loadAppRow(Context context, PackageManager pm, PackageInfo app) {
        final AppRow row = loadAppRow(context, pm, app.applicationInfo);
        row.systemApp = Utils.isSystemPackage(pm, app);
        return row;
    }

    public boolean getNotificationsBanned(String pkg, int uid) {
        try {
            final boolean enabled = sINM.areNotificationsEnabledForPackage(pkg, uid);
            return enabled;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean setNotificationsBanned(String pkg, int uid, boolean banned) {
        try {
            sINM.setNotificationsEnabledForPackage(pkg, uid, banned);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean getBypassZenMode(String pkg, int uid) {
        try {
            return sINM.getPriority(pkg, uid) == Notification.PRIORITY_MAX;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean setBypassZenMode(String pkg, int uid, boolean bypassZen) {
        try {
            sINM.setPriority(pkg, uid,
                    bypassZen ? Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public int getVisibilityOverride(String pkg, int uid) {
        try {
            return sINM.getVisibilityOverride(pkg, uid);
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE;
        }
    }


    public boolean setVisibilityOverride(String pkg, int uid, int override) {
        try {
            sINM.setVisibilityOverride(pkg, uid, override);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    //add by zhicang.liu begin..
    public boolean getSensitive(String pkg, int uid) {
        return getVisibilityOverride(pkg, uid) == Notification.VISIBILITY_PRIVATE;
    }

    public boolean setSensitive(String pkg, int uid, boolean sensitive) {
        int override = sensitive ? Notification.VISIBILITY_PRIVATE : NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE;
        return setVisibilityOverride(pkg, uid, override);
    }

    public boolean getSuperScript(String pkg, int uid) {
        try {
            return sINM.getPackageSuperScriptOverride(pkg, uid);
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean setSuperScript(String pkg, int uid, boolean superscript) {
        try {
            sINM.setPackageSuperScriptOverride(pkg, uid, superscript);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean getLockScreenNotify(String pkg, int uid) {
        try {
            return sINM.getPackageLockScreenNotifyOverride(pkg, uid);
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public boolean setLockScreenNotify(String pkg, int uid, boolean superscript) {
        try {
            sINM.setPackageLockScreenNotifyOverride(pkg, uid, superscript);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }
    //add by zhicang.liu end..

    public boolean setImportance(String pkg, int uid, int importance) {
        try {
            sINM.setImportance(pkg, uid, importance);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return false;
        }
    }

    public int getImportance(String pkg, int uid) {
        try {
            return sINM.getImportance(pkg, uid);
        } catch (Exception e) {
            Log.w(TAG, "Error calling NoMan", e);
            return NotificationListenerService.Ranking.IMPORTANCE_UNSPECIFIED;
        }
    }

    static class Row {
        public String section;
    }

    public static class AppRow extends Row {
        public String pkg;
        public int uid;
        public Drawable icon;
        public CharSequence label;
        //public Intent settingsIntent;
        public boolean banned;
        //public boolean first;  // first app in section
        public boolean systemApp;
        //public int appImportance;
        //public boolean appBypassDnd;
        //public int appVisOverride;
        //public boolean lockScreenSecure;
        // begin add by zhicang.liu
        public boolean sensitive;
        public boolean superscript;
        public boolean lockscreennotify;
        // end add by zhicang.liu
    }
}