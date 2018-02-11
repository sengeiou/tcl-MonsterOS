/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.vcard;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
//import android.util.Log;
import android.util.SparseArray;

import com.android.contacts.common.R;

import com.mediatek.storage.StorageManagerEx;
import com.mediatek.contacts.GlobalEnv;
import com.mediatek.contacts.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The class responsible for handling vCard import/export requests.
 *
 * This Service creates one ImportRequest/ExportRequest object (as Runnable) per request and push
 * it to {@link ExecutorService} with single thread executor. The executor handles each request
 * one by one, and notifies users when needed.
 */
// TODO: Using IntentService looks simpler than using Service + ServiceConnection though this
// works fine enough. Investigate the feasibility.
public class VCardService extends Service {
    private final static String LOG_TAG = "VCardService";

    /* package */ final static boolean DEBUG = true;

    /* package */ static final int MSG_IMPORT_REQUEST = 1;
    /* package */ static final int MSG_EXPORT_REQUEST = 2;
    /* package */ static final int MSG_CANCEL_REQUEST = 3;
    /* package */ static final int MSG_REQUEST_AVAILABLE_EXPORT_DESTINATION = 4;
    /* package */ static final int MSG_SET_AVAILABLE_EXPORT_DESTINATION = 5;

    /**
     * Specifies the type of operation. Used when constructing a notification, canceling
     * some operation, etc.
     */

    /**
     * M: Bug Fix CR ID: ALPS00313946 @{
     * Descriptions: can not edit contact when import vcard
     */
    public static final int TYPE_IMPORT = 1;
    public static final int TYPE_EXPORT = 2;
    /** @} */

    /* package */ static final String CACHE_FILE_PREFIX = "import_tmp_";

    /* package */ static final String X_VCARD_MIME_TYPE = "text/x-vcard";

    private class CustomMediaScannerConnectionClient implements MediaScannerConnectionClient {
        final MediaScannerConnection mConnection;
        final String mPath;

        public CustomMediaScannerConnectionClient(String path) {
            mConnection = new MediaScannerConnection(VCardService.this, this);
            mPath = path;
        }

        public void start() {
            mConnection.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            if (DEBUG) { Log.d(LOG_TAG, "Connected to MediaScanner. Start scanning."); }
            mConnection.scanFile(mPath, null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            if (DEBUG) { Log.d(LOG_TAG, "scan completed: " + path); }
            mConnection.disconnect();
            removeConnectionClient(this);
        }
    }

    // Should be single thread, as we don't want to simultaneously handle import and export
    // requests.
    private final ExecutorService mExecutorService = GlobalEnv.getSingleTaskService();

    public int mCurrentJobId;

    // Stores all unfinished import/export jobs which will be executed by mExecutorService.
    // Key is jobId.
    private static final SparseArray<ProcessorBase> RUNNINGJOBMAP =
            new SparseArray<ProcessorBase>();
    // Stores ScannerConnectionClient objects until they finish scanning requested files.
    // Uses List class for simplicity. It's not costly as we won't have multiple objects in
    // almost all cases.
    private final List<CustomMediaScannerConnectionClient> mRemainingScannerConnections =
            new ArrayList<CustomMediaScannerConnectionClient>();

    private MyBinder mBinder;

    private String mCallingActivity;

    /* ** vCard exporter params ** */
    // If true, VCardExporter is able to emits files longer than 8.3 format.
    private static final boolean ALLOW_LONG_FILE_NAME = false;

    private File mTargetDirectory;
    private String mFileNamePrefix;
    private String mFileNameSuffix;
    private int mFileIndexMinimum;
    private int mFileIndexMaximum;
    private String mFileNameExtension;
    private Set<String> mExtensionsToConsider;
    private String mErrorReason;

    /** M: ALPS01022668 first vcard import finished, second vcard still caching,
        can't stop service in this case. @{ */
    public boolean mCaching = false;
    /** @} */

    // File names currently reserved by some export job.
    private final Set<String> mReservedDestination = new HashSet<String>();
    /* ** end of vCard exporter params ** */

    public class MyBinder extends Binder {
        public VCardService getService() {
            return VCardService.this;
        }
    }

   @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new MyBinder();
        if (DEBUG) Log.d(LOG_TAG, "vCard Service is being created.");
        initExporterParams();
        /// M: change for low_memory kill Contacts process CR ALPS00609378.
        // startForeground(1,  new Notification());
    }

    private void initExporterParams() {
        /// M: @{
        // mTargetDirectory = Environment.getExternalStorageDirectory();
        /// @}
        mFileNamePrefix = getString(R.string.config_export_file_prefix);
        mFileNameSuffix = getString(R.string.config_export_file_suffix);
        mFileNameExtension = getString(R.string.config_export_file_extension);

        mExtensionsToConsider = new HashSet<String>();
        mExtensionsToConsider.add(mFileNameExtension);

        final String additionalExtensions =
            getString(R.string.config_export_extensions_to_consider);
        if (!TextUtils.isEmpty(additionalExtensions)) {
            for (String extension : additionalExtensions.split(",")) {
                String trimed = extension.trim();
                if (trimed.length() > 0) {
                    mExtensionsToConsider.add(trimed);
                }
            }
        }

        final Resources resources = getResources();
        mFileIndexMinimum = resources.getInteger(R.integer.config_export_file_min_index);
        mFileIndexMaximum = resources.getInteger(R.integer.config_export_file_max_index);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        Log.i(LOG_TAG, "[onStartCommand]intent = " + intent);
        if (intent != null && intent.getExtras() != null) {
            mCallingActivity = intent.getExtras().getString(
                    VCardCommonArguments.ARG_CALLING_ACTIVITY);
            Log.i(LOG_TAG, "[onStartCommand]mCallingActivity = " + mCallingActivity);
        } else {
            Log.i(LOG_TAG, "[onStartCommand]mCallingActivity = null");
            mCallingActivity = null;
        }
        /// M: Change START_STICKY to START_NOT_STICKY for Service slim
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(LOG_TAG, "VCardService is being destroyed.");
        cancelAllRequestsAndShutdown();
        clearCache();
        super.onDestroy();
    }

    public synchronized void handleImportRequest(List<ImportRequest> requests,
            VCardImportExportListener listener) {
        if (false) {
            final ArrayList<String> uris = new ArrayList<String>();
            final ArrayList<String> displayNames = new ArrayList<String>();
            for (ImportRequest request : requests) {
                uris.add(request.uri.toString());//M:uri may be null
                displayNames.add(request.displayName);
            }
            Log.d(LOG_TAG,
                    String.format("received multiple import request (uri: %s, displayName: %s)",
                            uris.toString(), displayNames.toString()));
        }
        final int size = requests.size();
        for (int i = 0; i < size; i++) {
            ImportRequest request = requests.get(i);

            if (tryExecute(new ImportProcessor(this, listener, request, mCurrentJobId))) {
                if (listener != null) {
                    listener.onImportProcessed(request, mCurrentJobId, i);
                }
                mCurrentJobId++;
            } else {
                if (listener != null) {
                    listener.onImportFailed(request);
                }
                // A rejection means executor doesn't run any more. Exit.
                break;
            }
        }
    }

    public synchronized void handleExportRequest(ExportRequest request,
            VCardImportExportListener listener) {
        if (tryExecute(new ExportProcessor(this, request, mCurrentJobId, mCallingActivity))) {
            final String path = request.destUri.getEncodedPath();
            if (DEBUG) Log.d(LOG_TAG, "Reserve the path " + path);
            if (!mReservedDestination.add(path)) {
                Log.w(LOG_TAG,
                        String.format("The path %s is already reserved. Reject export request",
                                path));
                if (listener != null) {
                    listener.onExportFailed(request);
                }
                return;
            }

            if (listener != null) {
                listener.onExportProcessed(request, mCurrentJobId);
            }
            mCurrentJobId++;
        } else {
            if (listener != null) {
                listener.onExportFailed(request);
            }
        }
    }

    /**
     * Tries to call {@link ExecutorService#execute(Runnable)} toward a given processor.
     * @return true when successful.
     */
    private synchronized boolean tryExecute(ProcessorBase processor) {
        try {
            if (DEBUG) {
                Log.d(LOG_TAG, "Executor service status: shutdown: " + mExecutorService.isShutdown()
                        + ", terminated: " + mExecutorService.isTerminated());
            }
            mExecutorService.execute(processor);
            RUNNINGJOBMAP.put(mCurrentJobId, processor);
            return true;
        } catch (RejectedExecutionException e) {
            Log.w(LOG_TAG, "Failed to excetute a job.", e);
            return false;
        }
    }

    public synchronized void handleCancelRequest(CancelRequest request,
            VCardImportExportListener listener) {
        final int jobId = request.jobId;
        if (DEBUG) Log.d(LOG_TAG, String.format("Received cancel request1. (id: %d)", jobId));

        final ProcessorBase processor = RUNNINGJOBMAP.get(jobId);
        RUNNINGJOBMAP.remove(jobId);

        if (processor != null) {
            processor.cancel(true);
            final int type = processor.getType();
            if (listener != null) {
                listener.onCancelRequest(request, type);
            }
            if (type == TYPE_EXPORT) {
                final String path =
                        ((ExportProcessor)processor).getRequest().destUri.getEncodedPath();
                Log.i(LOG_TAG,
                        String.format("Cancel reservation for the path %s if appropriate", path));
                if (!mReservedDestination.remove(path)) {
                    Log.w(LOG_TAG, "Not reserved.");
                }
            }
        } else {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        Log.d(LOG_TAG,"onCancelRequest2");
        stopServiceIfAppropriate();
    }

    /// M:
    public synchronized void handleRequestAvailableExportDestination(final Messenger messenger) {
        if (DEBUG) Log.d(LOG_TAG, "Received available export destination request.");
        /**
         * M: New Feature CR ID: ALPS00276020 @{
         * Descriptions: Support multiple storages for Contacts import/export function
         */
        getDirectory();
        /** @} */
        final String path = getAppropriateDestination(mTargetDirectory);
        final Message message;
        if (path != null) {
            message = Message.obtain(null,
                    VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION, 0, 0, path);
        } else {
            message = Message.obtain(null,
                    VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION,
                    R.id.dialog_fail_to_export_with_reason, 0, mErrorReason);
        }
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Failed to send reply for available export destination request.", e);
        }
    }

    /** M: New Feature CR ID: ALPS00276020 @{ */
    private void getDirectory() {
        // TODO need to check
        if (getDestStoragePath() == null) {
            mTargetDirectory = new File(StorageManagerEx.getDefaultPath());
        } else {
            mTargetDirectory = new File(getDestStoragePath());
        }
        Log.d(LOG_TAG, "mTargetDirectory : " + mTargetDirectory);
    }
    /** @} */

    /**
     * Checks job list and call {@link #stopSelf()} when there's no job and no scanner connection
     * is remaining.
     * A new job (import/export) cannot be submitted any more after this call.
     */
    private synchronized void stopServiceIfAppropriate() {
        if (RUNNINGJOBMAP.size() > 0) {
            final int size = RUNNINGJOBMAP.size();

            // Check if there are processors which aren't finished yet. If we still have ones to
            // process, we cannot stop the service yet. Also clean up already finished processors
            // here.

            // Job-ids to be removed. At first all elements in the array are invalid and will
            // be filled with real job-ids from the array's top. When we find a not-yet-finished
            // processor, then we start removing those finished jobs. In that case latter half of
            // this array will be invalid.
            final int[] toBeRemoved = new int[size];
            for (int i = 0; i < size; i++) {
                final int jobId = RUNNINGJOBMAP.keyAt(i);
                final ProcessorBase processor = RUNNINGJOBMAP.valueAt(i);
                if (!processor.isDone()) {
                    Log.i(LOG_TAG, String.format("Found unfinished job (id: %d)", jobId));

                    // Remove processors which are already "done", all of which should be before
                    // processors which aren't done yet.
                    for (int j = 0; j < i; j++) {
                        RUNNINGJOBMAP.remove(toBeRemoved[j]);
                    }
                    return;
                }

                // Remember the finished processor.
                toBeRemoved[i] = jobId;
            }

            // We're sure we can remove all. Instead of removing one by one, just call clear().
            RUNNINGJOBMAP.clear();
        }

        if (!mRemainingScannerConnections.isEmpty()) {
            Log.d(LOG_TAG, "MediaScanner update is in progress.");
            return;
        }
        /** M: ALPS01022668 first vcard import finished, second vcard still caching,
            can't stop service in this case. @{ */
        if (mCaching) {
            Log.d(LOG_TAG, "caching vcard file, can't stop service");
            return;
        }
        /**@}*/

        Log.i(LOG_TAG, "No unfinished job. Stop this service.");
        /// M: @{
        //mExecutorService.shutdown();
        /// @}
        stopSelf();
    }

    /* package */ synchronized void updateMediaScanner(String path) {
        if (DEBUG) {
            Log.d(LOG_TAG, "MediaScanner is being updated: " + path);
        }

        if (mExecutorService.isShutdown()) {
            Log.w(LOG_TAG, "MediaScanner update is requested after executor's being shut down. " +
                    "Ignoring the update request");
            return;
        }
        final CustomMediaScannerConnectionClient client =
                new CustomMediaScannerConnectionClient(path);
        mRemainingScannerConnections.add(client);
        client.start();
    }

    private synchronized void removeConnectionClient(
            CustomMediaScannerConnectionClient client) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Removing custom MediaScannerConnectionClient.");
        }
        mRemainingScannerConnections.remove(client);
        stopServiceIfAppropriate();
    }

    /* package */ synchronized void handleFinishImportNotification(
            int jobId, boolean successful) {
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("Received vCard import finish notification (id: %d). "
                    + "Result: %b", jobId, (successful ? "success" : "failure")));
        }
        RUNNINGJOBMAP.remove(jobId);
        stopServiceIfAppropriate();
    }

    /* package */ synchronized void handleFinishExportNotification(
            int jobId, boolean successful) {
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("Received vCard export finish notification (id: %d). "
                    + "Result: %b", jobId, (successful ? "success" : "failure")));
        }
        final ProcessorBase job = RUNNINGJOBMAP.get(jobId);
        RUNNINGJOBMAP.remove(jobId);
        if (job == null) {
            Log.w(LOG_TAG, String.format("Tried to remove unknown job (id: %d)", jobId));
        } else if (!(job instanceof ExportProcessor)) {
            Log.w(LOG_TAG,
                    String.format("Removed job (id: %s) isn't ExportProcessor", jobId));
        } else {
            final String path = ((ExportProcessor)job).getRequest().destUri.getEncodedPath();
            if (DEBUG) Log.d(LOG_TAG, "Remove reserved path " + path);
            mReservedDestination.remove(path);
        }

        stopServiceIfAppropriate();
    }

    /**
     * Cancels all the import/export requests and calls {@link ExecutorService#shutdown()}, which
     * means this Service becomes no longer ready for import/export requests.
     *
     * Mainly called from onDestroy().
     */
    private synchronized void cancelAllRequestsAndShutdown() {
        for (int i = 0; i < RUNNINGJOBMAP.size(); i++) {
            RUNNINGJOBMAP.valueAt(i).cancel(true);
        }
        RUNNINGJOBMAP.clear();
        /// M: @{
        //mExecutorService.shutdown();
        /// @}
    }

    /**
     * Removes import caches stored locally.
     */
    private void clearCache() {
        for (final String fileName : fileList()) {
            if (fileName.startsWith(CACHE_FILE_PREFIX)) {
                // We don't want to keep all the caches so we remove cache files old enough.
                Log.i(LOG_TAG, "Remove a temporary file: " + fileName);
                deleteFile(fileName);
            }
        }
    }

    /**
     * M:Returns an appropriate file name for vCard export. Returns null when impossible.
     *
     * @return destination path for a vCard file to be exported. null on error and mErrorReason
     * is correctly set.
     */
    private String getAppropriateDestination(final File destDirectory) {
        /*
         * Here, file names have 5 parts: directory, prefix, index, suffix, and extension.
         * e.g. "/mnt/sdcard/prfx00001sfx.vcf" -> "/mnt/sdcard", "prfx", "00001", "sfx", and ".vcf"
         *      (In default, prefix and suffix is empty, so usually the destination would be
         *       /mnt/sdcard/00001.vcf.)
         *
         * This method increments "index" part from 1 to maximum, and checks whether any file name
         * following naming rule is available. If there's no file named /mnt/sdcard/00001.vcf, the
         * name will be returned to a caller. If there are 00001.vcf 00002.vcf, 00003.vcf is
         * returned. We format these numbers in the US locale to ensure we they appear as
         * english numerals.
         *
         * There may not be any appropriate file name. If there are 99999 vCard files in the
         * storage, for example, there's no appropriate name, so this method returns
         * null.
         */

        // Count the number of digits of mFileIndexMaximum
        // e.g. When mFileIndexMaximum is 99999, fileIndexDigit becomes 5, as we will count the
        // change for ALPS02314302, make the arithmetic simple.
        int fileIndexDigit = String.valueOf(mFileIndexMaximum).length();

        // %s05d%s (e.g. "p00001s")
        final String bodyFormat = "%s%0" + fileIndexDigit + "d%s";

        if (!ALLOW_LONG_FILE_NAME) {
            final String possibleBody =
                    String.format(Locale.US, bodyFormat, mFileNamePrefix, 1, mFileNameSuffix);
            if (possibleBody.length() > 8 || mFileNameExtension.length() > 3) {
                Log.e(LOG_TAG, "This code does not allow any long file name.");
                mErrorReason = getString(R.string.fail_reason_too_long_filename,
                        String.format("%s.%s", possibleBody, mFileNameExtension));
                Log.w(LOG_TAG, "File name becomes too long.");
                return null;
            }
        }

        for (int i = mFileIndexMinimum; i <= mFileIndexMaximum; i++) {
            boolean numberIsAvailable = true;
            final String body
                    = String.format(Locale.US, bodyFormat, mFileNamePrefix, i, mFileNameSuffix);
            // Make sure that none of the extensions of mExtensionsToConsider matches. If this
            // number is free, we'll go ahead with mFileNameExtension (which is included in
            // mExtensionsToConsider)
            for (String possibleExtension : mExtensionsToConsider) {
                final File file = new File(destDirectory, body + "." + possibleExtension);
                final String path = file.getAbsolutePath();
                synchronized (this) {
                    // Is this being exported right now? Skip this number
                    if (mReservedDestination.contains(path)) {
                        if (DEBUG) {
                            Log.d(LOG_TAG, String.format("%s is already being exported.", path));
                        }
                        numberIsAvailable = false;
                        break;
                    }
                }
                if (file.exists()) {
                    numberIsAvailable = false;
                    break;
                }
            }
            if (numberIsAvailable) {
                return new File(destDirectory, body + "." + mFileNameExtension).getAbsolutePath();
            }
        }

        Log.w(LOG_TAG, "Reached vCard number limit. Maybe there are too many vCard in the storage");
        mErrorReason = getString(R.string.fail_reason_too_many_vcard);
        return null;
    }

    /// M: @{
    private String mQuerySelection = null;
    private String mDestPath = null; // The destination storage path, for exemple: mnt/sdcard2

    public void setQuerySelection(String selection) {
        mQuerySelection = selection;
    }

    public String getQuerySelection() {
        return mQuerySelection;
    }

    public void setDestStoragePath(String path) {
        mDestPath = path;
    }

    public String getDestStoragePath() {
        return mDestPath;
    }

    /**
     * M: Bug Fix CR ID: ALPS00313946 @{
     * Descriptions: can not edit contact when import vcard
     */
    public static synchronized boolean isProcessing(int requestType) {
        Log.d(LOG_TAG, "RUNNINGJOBMAP.size() : " + RUNNINGJOBMAP.size());
        if (RUNNINGJOBMAP.size() <= 0) {
            return false;
        }

        if (RUNNINGJOBMAP.size() > 0) {
            for (int i = 0; i < RUNNINGJOBMAP.size(); i++) {
                final ProcessorBase processor = RUNNINGJOBMAP.valueAt(i);
                Log.d("yongjian", "processor.getType()1 : " + processor.getType()
                        + " | requestType : " + requestType);
                if (processor.getType() == requestType) {
                    return true;
                }
            }
        }

        return false;
    }
    /** @} */
    /// @}
}
