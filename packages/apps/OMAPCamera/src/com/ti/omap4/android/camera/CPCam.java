/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.ti.omap4.android.camera;

import com.ti.omap4.android.camera.CPCamFocusManager.QueuedShotStates;
import com.ti.omap4.android.camera.ui.CameraPicker;
import com.ti.omap4.android.camera.ui.FaceView;
import com.ti.omap4.android.camera.ui.FaceViewData;
import com.ti.omap4.android.camera.ui.IndicatorControlContainer;
import com.ti.omap4.android.camera.ui.ManualGainExposureSettings;
import com.ti.omap4.android.camera.ui.PopupManager;
import com.ti.omap4.android.camera.ui.Rotatable;
import com.ti.omap4.android.camera.ui.RotateImageView;
import com.ti.omap4.android.camera.ui.RotateLayout;
import com.ti.omap4.android.camera.ui.RotateTextToast;
import com.ti.omap4.android.camera.ui.SharePopup;
import com.ti.omap4.android.camera.ui.CPcamExposureControl;
import com.ti.omap4.android.camera.ui.CPcamGainControl;
import javax.microedition.khronos.egl.EGL10;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera.CameraInfo;

import com.ti.omap.android.cpcam.CPCam.Face;
import com.ti.omap.android.cpcam.CPCam.FaceDetectionListener;
import com.ti.omap.android.cpcam.CPCam.Parameters;
import com.ti.omap.android.cpcam.CPCam.PictureCallback;
import com.ti.omap.android.cpcam.CPCam.PreviewCallback;
import com.ti.omap.android.cpcam.CPCam.Size;
import com.ti.omap.android.cpcam.CPCamBufferQueue;
import com.ti.omap.android.cpcam.CPCamMetadata;
import android.media.MediaActionSound;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.StringTokenizer;

/** The Camera activity which can preview and take pictures. */
public class CPCam extends ActivityBase implements CPCamFocusManager.Listener,
        View.OnTouchListener, ShutterButton.OnShutterButtonListener,
        SurfaceHolder.Callback, ModePicker.OnModeChangeListener,
        FaceDetectionListener,CameraPreference.OnPreferenceChangedListener,
        TouchManager.Listener, com.ti.omap.android.cpcam.CPCamBufferQueue.OnFrameAvailableListener,
        LocationManager.Listener, ShutterButton.OnShutterButtonLongPressListener {

    private static final String TAG = "CPCam";

    private static final int CROP_MSG = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int CLEAR_SCREEN_DELAY = 3;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    private static final int CHECK_DISPLAY_ROTATION = 5;
    private static final int SHOW_TAP_TO_FOCUS_TOAST = 6;
    private static final int UPDATE_THUMBNAIL = 7;

    private static final int MODE_RESTART = 9;
    private static final int RESTART_PREVIEW = 10;
    public static final int MANUAL_GAIN_EXPOSURE_CHANGED = 12;
    public static final int RELEASE_CAMERA = 13;
    private static final int QUEUE_NEXT_SHOT = 14;
    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_MODE = 8;

    //CPCam
    private com.ti.omap.android.cpcam.CPCam mCPCamDevice;
    private String mShotParamsGain = "400"; //Default values
    private String mShotParamsExposure = "40000";
    private static final String DEFAULT_EXPOSURE_GAIN = "(40000,400)";
    private static final String ABSOLUTE_EXP_GAIN_TEXT = "Absolute";
    private static final String RELATIVE_EXP_GAIN_TEXT = "Relative";
    private com.ti.omap.android.cpcam.CPCamBufferQueue mTapOut;
    Context mContext;
    private int mFrameWidth,mFrameHeight;
    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final String ANALOG_GAIN_METADATA = "analog-gain";
    private static final String ANALOG_GAIN_REQUESTED_METADATA = "analog-gain-req";
    private static final String EXPOSURE_TIME_METADATA = "exposure-time";
    private static final String EXPOSURE_TIME_REQUESTED_METADATA = "exposure-time-req";

    private static final String PARM_IPP = "ipp";
    private static final String PARM_IPP_NONE = "off";

    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int CAMERA_RELEASE_DELAY = 1000;

    private CPcamGainControl mCpcamGainControl;
    private CPcamExposureControl mCpcamExposureControl;

    private com.ti.omap.android.cpcam.CPCam.Parameters mParameters;
    private com.ti.omap.android.cpcam.CPCam.Parameters mInitialParams;
    private com.ti.omap.android.cpcam.CPCam.Parameters mShotParams;
    private boolean mFocusAreaSupported;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails. Ex: if the value
    // is 90, the UI components should be rotated 90 degrees counter-clockwise.
    private int mOrientationCompensation = 0;
    private ComboPreferences mPreferences;

    private static final String sTempCropFilename = "crop-temp";

    private ContentProviderClient mMediaProviderClient;
    private SurfaceHolder mSurfaceHolder = null;
    private ShutterButton mShutterButton;
    private GestureDetector mPopupGestureDetector;
    private GestureDetector mGestureDetector;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;
    public  static boolean mIsTestExecuting = false;
    private boolean mFaceDetectionStarted = false;

    private View mPreviewPanel;  // The container of PreviewFrameLayout.
    private PreviewFrameLayout mPreviewFrameLayout;
    private View mPreviewFrame;  // Preview frame area.
    private RotateDialogController mRotateDialog;

    // A popup window that contains a bigger thumbnail and a list of apps to share.
    private SharePopup mSharePopup;
    // The bitmap of the last captured picture thumbnail and the URI of the
    // original picture.
    private Thumbnail mThumbnail;
    // An imageview showing showing the last captured picture thumbnail.
    private RotateImageView mThumbnailView;
    private ModePicker mModePicker;
    private FaceView mFaceView;
    private RotateLayout mFocusAreaIndicator;
    private Rotatable mReviewCancelButton;
    private Rotatable mReviewDoneButton;
    private Button mReprocessButton;
    private Button mExpGainButton;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    // Small indicators which show the camera settings in the viewfinder.
    private ImageView mFocusIndicator;
    private TextView mMetaDataIndicator;
    // A view group that contains all the small indicators.
    private Rotatable mOnScreenIndicators;
    // We use a thread in ImageSaver to do the work of saving images and
    // generating thumbnails. This reduces the shot-to-shot time.
    private ImageSaver mImageSaver;
    private MediaActionSound mCameraSound;

    private S3DViewWrapper s3dView;
    private boolean mS3dViewEnabled = false;

    /**
     * An unpublished intent flag requesting to return as soon as capturing
     * is completed.
     *
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;

    private static final int PREVIEW_STOPPED = 0;
    private static final int IDLE = 1;  // preview is active
    // Focus is in progress. The exact focus state is in Focus.java.
    private static final int FOCUSING = 2;
    private static final int SNAPSHOT_IN_PROGRESS = 3;
    private static final int QUEUED_SHOT_IN_PROGRESS = 4;
    private int mCameraState = PREVIEW_STOPPED;
    private Object mCameraStateLock = new Object();

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final JpegPictureCallback mJpegCallback =
            new JpegPictureCallback(null);
    private final CPCameraErrorCallback mErrorCallback = new CPCameraErrorCallback();
    private static final String PARM_SENSOR_ORIENTATION = "sensor-orientation";

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    private String mCPCamMode;
    private boolean mReprocessNextFrame = false;
    private boolean mRestartQueueShot = false;

    private boolean mIsRelativeExposureGainPair = false;

    private String mCaptureMode = "cp-cam";
    private String mPreviewSize = null;
    public  static String mIMGscriptTitle;
    private String mPreviewLayout = null;
    private boolean mIsPreviewLayoutInit = false;
    private boolean mIsCaptureLayoutInit = false;
    private String mCaptureLayout = null;
    private String mPictureFormat = null;
    private long mFocusStartTime;
    private long mCaptureStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private long mPicturesRemaining;
    private byte[] mJpegImageData;
    private int mManualExposureControl;
    private int mManualGainISO;
    private int mManualExposureControlValue = 40; //Default values
    private int mManualGainControlValue = 400;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;

    private TouchManager mTouchManager;

    // This handles everything about focus.
    private CPCamFocusManager mFocusManager;
    private Toast mNotSelectableToast;
    private boolean mTouchFocusEnabled = false;

    private final Handler mHandler = new MainHandler();
    private IndicatorControlContainer mIndicatorControlContainer;
    private PreferenceGroup mPreferenceGroup;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId;

    private boolean mQuickCapture;
    private IntentFilter mTestIntent = null;

    // This state is saved at the start of onPause, and used for further
    // state retrieve in onResume
    private int mCameraSavedState = PREVIEW_STOPPED;

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESTART_PREVIEW: {
                    boolean updateAllParams = true;
                    if ( msg.arg1 == MODE_RESTART ) {
                        updateAllParams = false;
                    }
                    startPreview(updateAllParams);
                    startFaceDetection();
                    if (mJpegPictureCallbackTime != 0) {
                        long now = System.currentTimeMillis();
                        mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                        Log.v(TAG, "mJpegCallbackFinishTime = "
                                + mJpegCallbackFinishTime + "ms");
                        mJpegPictureCallbackTime = 0;
                    }
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Set the display orientation if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if (Util.getDisplayRotation(CPCam.this) != mDisplayRotation
                            && isCameraIdle()) {
                        startPreview(true);
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case SHOW_TAP_TO_FOCUS_TOAST: {
                    showTapToFocusToast();
                    break;
                }

                case UPDATE_THUMBNAIL: {
                    mImageSaver.updateThumbnail();
                    break;
                }
                case MANUAL_GAIN_EXPOSURE_CHANGED: {
                    Bundle data;
                    data = msg.getData();
                    mManualExposureControl = data.getInt("EXPOSURE");
                    mManualGainISO = data.getInt("ISO");

                    if(mIsRelativeExposureGainPair) {
                        //set Relative exposure and gain values
                        mManualExposureControl = mManualExposureControl
                                -10*mParameters.getMaxExposureCompensation();
                        mManualGainISO = mManualGainISO
                                -10*mParameters.getMaxExposureCompensation();
                        // decrement with 300 to get the range (-300;+300 )
                        if (mManualExposureControl < 10*mParameters.getMinExposureCompensation())
                            mManualExposureControl = 10*mParameters.getMinExposureCompensation();

                        if(mManualExposureControl > 0) {
                            mShotParamsExposure = "+" + Integer.toString(mManualExposureControl);
                        } else {
                            mShotParamsExposure = Integer.toString(mManualExposureControl);
                        }

                        if ( mManualGainISO <= 10*mParameters.getMinExposureCompensation())
                            mManualGainISO = 10*mParameters.getMinExposureCompensation();

                        if(mManualGainISO > 0) {
                            mShotParamsGain = "+" + Integer.toString(mManualGainISO);
                        } else {
                            mShotParamsGain = Integer.toString(mManualGainISO);
                        }

                    } else {
                        //Set Absolute exposure and gain values
                        if (mManualExposureControl <= 0) {
                            mManualExposureControl = 1;
                        }
                        mShotParamsExposure = Integer.toString(1000*mManualExposureControl);

                        if ( mManualGainISO <= 0) {
                            mManualGainISO = 0;
                        }
                        mShotParamsGain = Integer.toString(mManualGainISO);
                    }
                    Log.e(TAG,mIsRelativeExposureGainPair
                    + mShotParamsExposure + " , " + mShotParamsGain);
                    String expGainPair = new String( "(" + mShotParamsExposure + "," + mShotParamsGain + ")" );

                    if ( null == mShotParams && null != mCPCamDevice ) {
                        mShotParams = mCPCamDevice.getParameters();
                    }

                    mShotParams.setPictureFormat(ImageFormat.NV21);
                    mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_EXP_GAIN_PAIRS, expGainPair);
                    mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_BURST, 1);

                    break;
                }
                case QUEUE_NEXT_SHOT: {
                    if ( null != mCPCamDevice ) {
                        mCPCamDevice.takePicture(null, null, null, mJpegCallback, mShotParams);
                    }
                    break;
                }
                case RELEASE_CAMERA: {
                    stopPreview();
                    closeCamera();
                    break;
                }
            }
        }
    }


    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = getContentResolver()
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized) return;

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        mOrientationListener = new MyOrientationEventListener(CPCam.this);
        mOrientationListener.enable();

        // Initialize location sevice.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        initOnScreenIndicator();
        mLocationManager.recordLocation(recordLocation);

        keepMediaProviderInstance();
        checkStorage();

        // Initialize last picture button.
        mContentResolver = getContentResolver();
        if (!mIsImageCaptureIntent) {  // no thumbnail in image capture intent
            initThumbnailButton();
        }

        // Initialize shutter button.
        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.setOnShutterButtonLongPressListener(this);
        mShutterButton.setVisibility(View.VISIBLE);

        // Initialize focus UI.
        mPreviewFrame = findViewById(R.id.camera_preview);
        mPreviewFrame.setOnTouchListener(this);
        mFocusAreaIndicator = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.initialize(mFocusAreaIndicator, mPreviewFrame, null, this,
                mirror, mDisplayOrientation);
        mImageSaver = new ImageSaver();
        Util.initializeScreenBrightness(getWindow(), getContentResolver());
        initializeCPcamSliders(mIsRelativeExposureGainPair);
        startFaceDetection();
        // Show the tap to focus toast if this is the first start.
        if (mFocusAreaSupported &&
                mPreferences.getBoolean(CPCameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, true)) {
            // Delay the toast for one second to wait for orientation.
            mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_FOCUS_TOAST, 1000);
        }

        mFirstTimeInitialized = true;
        addIdleHandler();
        mTapOut = new CPCamBufferQueue(true);
        mTapOut.setOnFrameAvailableListener(this);

        try {
            mCPCamDevice.setBufferSource(null, mTapOut);
        } catch (IOException ioe) {
            Log.e(TAG, "Error trying to setBufferSource!");
        }
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    private void initThumbnailButton() {
        // Load the thumbnail from the disk.
        mThumbnail = Thumbnail.loadFrom(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
        updateThumbnailButton();
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        if ((mThumbnail == null || !Util.isUriValid(mThumbnail.getUri(), mContentResolver))
                && mPicturesRemaining >= 0) {
            mThumbnail = Thumbnail.getLastThumbnail(mContentResolver);
        }
        if (mThumbnail != null) {
            mThumbnailView.setBitmap(mThumbnail.getBitmap());
        } else {
            mThumbnailView.setBitmap(null);
        }
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        mOrientationListener.enable();

        // Start location update if needed.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        mLocationManager.recordLocation(recordLocation);

        mImageSaver = new ImageSaver();
        initializeCPcamSliders(mIsRelativeExposureGainPair);
        keepMediaProviderInstance();
        checkStorage();
        hidePostCaptureAlert();

        if (!mIsImageCaptureIntent) {
            updateThumbnailButton();
            mModePicker.setCurrentMode(ModePicker.MODE_CPCAM);
        }
    }

    private class CpcamGainChangeListener implements CPcamGainControl.OnGainChangedListener {
        public void onGainValueChanged(int index) {
            CPCam.this.onCpcamGainValueChanged(index);
        }
    }

    private class CpcamExposureChangeListener implements CPcamExposureControl.OnExposureChangedListener {
        public void onExposureValueChanged(int index) {
            CPCam.this.onCpcamExposureValueChanged(index);
        }
    }

    private void initializeCPcamSliders(boolean mIsRelativeExposureGainPair) {

        int expMin,expMax,isoMax,isoStep,isoMin;
        if(mIsRelativeExposureGainPair){
            //Relative exposure and gain values
            //Set the range [0:600]
            expMin = 0;
            expMax = 20*mParameters.getMaxExposureCompensation();
            isoMin = 0;
            isoMax = 20*mParameters.getMaxExposureCompensation();
        } else {
            //Absolute exposure and gain values
            expMin = Integer.parseInt(mParameters.get(CPCameraSettings.KEY_SUPPORTED_MANUAL_EXPOSURE_MIN));
            expMax = Integer.parseInt(mParameters.get(CPCameraSettings.KEY_SUPPORTED_MANUAL_EXPOSURE_MAX));
            isoMin = Integer.parseInt(mParameters.get(CPCameraSettings.KEY_SUPPORTED_MANUAL_GAIN_ISO_MIN));
            isoMax = Integer.parseInt(mParameters.get(CPCameraSettings.KEY_SUPPORTED_MANUAL_GAIN_ISO_MAX));
        }

        mCpcamGainControl.setGainMinMax(isoMin,isoMax);
        mCpcamExposureControl.setExposureMinMax(expMin,expMax);

        mCpcamGainControl.setOnGainChangeListener(new CpcamGainChangeListener());
        mCpcamExposureControl.setOnExposureChangeListener(new CpcamExposureChangeListener());
    }

    private void onCpcamGainValueChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPausing) return;
        Message msg = new Message();
        Bundle data;
        data = new Bundle ();
        mManualGainControlValue = index;
        data.putInt("ISO", index);
        data.putInt("EXPOSURE", mManualExposureControlValue);
        msg.what = Camera.MANUAL_GAIN_EXPOSURE_CHANGED;
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void onCpcamExposureValueChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPausing) return;
        Message msg = new Message();
        Bundle data;
        data = new Bundle ();
        mManualExposureControlValue = index;
        data.putInt("EXPOSURE", index);
        data.putInt("ISO", mManualGainControlValue);
        msg.what = Camera.MANUAL_GAIN_EXPOSURE_CHANGED;
        msg.setData(data);
        mHandler.sendMessage(msg);
     }

    @Override
    public void startFaceDetection() {
        if (mFaceDetectionStarted || mCameraState != IDLE) return;
        if ( ( mParameters.getMaxNumDetectedFaces() > 0 ) && ( null != mCPCamDevice )) {
            mFaceDetectionStarted = true;
            mFaceView = (FaceView) findViewById(R.id.face_view);
            mFaceView.clear();
            mFaceView.setVisibility(View.VISIBLE);
            mFaceView.setDisplayOrientation(mDisplayOrientation);
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mFaceView.setMirror(info.facing == CameraInfo.CAMERA_FACING_FRONT);
            mFaceView.resume();
            mCPCamDevice.setFaceDetectionListener(this);
            try {
                mCPCamDevice.startFaceDetection();
            } catch ( RuntimeException e ) {
                Log.e(TAG, "Face detection already started. ", e);
                return;
            }
        }
    }

    @Override
    public void stopFaceDetection() {
        if (!mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCPCamDevice.setFaceDetectionListener(null);
            try {
                mCPCamDevice.stopFaceDetection();
            } catch (RuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "Face detection already stopped!");
            }
            if (mFaceView != null) mFaceView.clear();
        }
    }

    private class PopupGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            // Check if the popup window is visible.
            View popup = mIndicatorControlContainer.getActiveSettingPopup();
            if (popup == null) return false;


            // Let popup window, indicator control or preview frame handle the
            // event by themselves. Dismiss the popup window if users touch on
            // other areas.
            if (!Util.pointInView(e.getX(), e.getY(), popup)
                    && !Util.pointInView(e.getX(), e.getY(), mIndicatorControlContainer)
                    && !Util.pointInView(e.getX(), e.getY(), mPreviewFrame)) {
                mIndicatorControlContainer.dismissSettingPopup();
                // Let event fall through.
            }
            return false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        // Check if the popup window should be dismissed first.
        if ( (mPopupGestureDetector != null && mPopupGestureDetector.onTouchEvent(m)) ||
             (mGestureDetector != null && mGestureDetector.onTouchEvent(m)) ) {
            return true;
        }
        return super.dispatchTouchEvent(m);
    }

    private void initOnScreenIndicator() {
        mFocusIndicator = (ImageView) findViewById(R.id.onscreen_focus_indicator);
        mMetaDataIndicator = (TextView) findViewById(R.id.onscreen_metadata_indicator);
    }

    private void updateMetadataIndicator(String exposure,
                                         String exposureReq,
                                         String gain,
                                         String gainReq) {
        String metadata = new String();
        try {
            int expTime = Integer.parseInt(exposure) / 1000;
            int expTimeReq = Integer.parseInt(exposureReq) / 1000;
            metadata =  "Exposure[ms]: " + expTime + "\n" ;
            metadata += "Exposure Requested[ms]: " + expTimeReq + "\n";
        } catch ( NumberFormatException e ) { e.printStackTrace(); }

        metadata += "Gain: " + gain + "\n";
        metadata += "Gain Requested: " + gainReq;
        mMetaDataIndicator.setText(metadata);
        mMetaDataIndicator.setVisibility(View.VISIBLE);
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {

    }

    @Override
    public void hideGpsOnScreenIndicator() {

    }

    private void updateOnScreenMetadataIndicators(CPCamMetadata metaData) {
        if (metaData == null) return;
        String exposure = Integer.toString(metaData.exposureTime);
        String exposureRequested = Integer.toString(metaData.exposureTimeReq);
        String gain = Integer.toString(metaData.analogGain);
        String gainRequested = Integer.toString(metaData.analogGainReq);

        updateMetadataIndicator(exposure,
                                exposureRequested,
                                gain,
                                gainRequested);

    }

    private final class ShutterCallback
            implements com.ti.omap.android.cpcam.CPCam.ShutterCallback {
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
            mFocusManager.onShutter();
        }
    }

    private final class PostViewPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] data, com.ti.omap.android.cpcam.CPCam camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] rawData, com.ti.omap.android.cpcam.CPCam camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        public void onPictureTaken(
                final byte [] jpegData, final com.ti.omap.android.cpcam.CPCam camera) {
            if (mPausing) {
                return;
            }
            // WA: Re-create CPCamBufferQueue before next shot
            try {
                camera.setBufferSource(null, null);
                mTapOut.release();
                mTapOut = new CPCamBufferQueue(true);
                mTapOut.setOnFrameAvailableListener(CPCam.this);
                camera.setBufferSource(null, mTapOut);
            } catch(IOException e) { e.printStackTrace(); }

            if ( mReprocessNextFrame ) {
                mReprocessNextFrame = false;
            }

            mJpegPictureCallbackTime = System.currentTimeMillis();
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            if (!mIsImageCaptureIntent) {
                enableCameraControls(true);
                // We want to show the taken picture for a while, so we wait
                // for at least 0.5 second before restarting the preview.
                long delay = 500 - mPictureDisplayedToJpegCallbackTime;
                if (delay < 0) {
                    startPreview(true);
                    startFaceDetection();
                } else {
                    mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, delay);
                }

            }

            if (!mIsImageCaptureIntent) {
                Size s = mParameters.getPictureSize();
                mImageSaver.addImage(jpegData, mLocation, s.width, s.height);
            } else {
                mJpegImageData = jpegData;
                if (!mQuickCapture) {
                    showPostCaptureAlert();
                } else {
                    doAttach();
                }
            }

            // Check this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card in
            // the mean time and fill it, but that could have happened between the
            // shutter press and saving the JPEG too.
            checkStorage();

            if (!mHandler.hasMessages(RESTART_PREVIEW)) {
                long now = System.currentTimeMillis();
                mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                Log.v(TAG, "mJpegCallbackFinishTime = "
                        + mJpegCallbackFinishTime + "ms");
                mJpegPictureCallbackTime = 0;
            }
        }
    }

    private final class AutoFocusCallback
            implements com.ti.omap.android.cpcam.CPCam.AutoFocusCallback {
        public void onAutoFocus(
                boolean focused, com.ti.omap.android.cpcam.CPCam camera) {
            if (mPausing) return;

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            setCameraState(IDLE);

            // If focus completes and the snapshot is not started, enable the
            // controls.
            if (mFocusManager.isFocusCompleted()) {
                enableCameraControls(true);
            }

            mFocusManager.onAutoFocus(focused);
        }
    }

    // Each SaveRequest remembers the data needed to save an image.
    private static class SaveRequest {
        byte[] data;
        Location loc;
        int width, height;
        long dateTaken;
        int previewWidth;
    }

    // We use a queue to store the SaveRequests that have not been completed
    // yet. The main thread puts the request into the queue. The saver thread
    // gets it from the queue, does the work, and removes it from the queue.
    //
    // There are several cases the main thread needs to wait for the saver
    // thread to finish all the work in the queue:
    // (1) When the activity's onPause() is called, we need to finish all the
    // work, so other programs (like Gallery) can see all the images.
    // (2) When we need to show the SharePop, we need to finish all the work
    // too, because we want to show the thumbnail of the last image taken.
    //
    // If the queue becomes too long, adding a new request will block the main
    // thread until the queue length drops below the threshold (QUEUE_LIMIT).
    // If we don't do this, we may face several problems: (1) We may OOM
    // because we are holding all the jpeg data in memory. (2) We may ANR
    // when we need to wait for saver thread finishing all the work (in
    // onPause() or showSharePopup()) because the time to finishing a long queue
    // of work may be too long.
    private class ImageSaver extends Thread {
        private static final int QUEUE_LIMIT = 15;

        private ArrayList<SaveRequest> mQueue;
        private Thumbnail mPendingThumbnail;
        private Object mUpdateThumbnailLock = new Object();
        private boolean mStop;

        // Runs in main thread
        public ImageSaver() {
            mQueue = new ArrayList<SaveRequest>();
            start();
        }

        // Runs in main thread
        public void addImage(final byte[] data, Location loc, int width,
                int height) {
            SaveRequest r = new SaveRequest();
            r.data = data;
            r.loc = (loc == null) ? null : new Location(loc);  // make a copy
            r.width = width;
            r.height = height;
            r.dateTaken = System.currentTimeMillis();
            if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                r.previewWidth = mPreviewFrameLayout.getHeight();
            } else {
                r.previewWidth = mPreviewFrameLayout.getWidth();
            }
            synchronized (this) {
                while (mQueue.size() >= QUEUE_LIMIT) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
                mQueue.add(r);
                notifyAll();  // Tell saver thread there is new work to do.
            }
        }

        // Runs in saver thread
        @Override
        public void run() {
            while (true) {
                SaveRequest r;
                synchronized (this) {
                    if (mQueue.isEmpty()) {
                        notifyAll();  // notify main thread in waitDone

                        // Note that we can only stop after we saved all images
                        // in the queue.
                        if (mStop) break;

                        try {
                            wait();
                        } catch (InterruptedException ex) {
                            // ignore.
                        }
                        continue;
                    }
                    r = mQueue.get(0);
                }
                storeImage(r.data, r.loc, r.width, r.height, r.dateTaken,
                        r.previewWidth);
                synchronized(this) {
                    mQueue.remove(0);
                    notifyAll();  // the main thread may wait in addImage
                }
            }
        }

        // Runs in main thread
        public void waitDone() {
            synchronized (this) {
                while (!mQueue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
            }
            updateThumbnail();
        }

        // Runs in main thread
        public void finish() {
            waitDone();
            synchronized (this) {
                mStop = true;
                notifyAll();
            }
            try {
                join();
            } catch (InterruptedException ex) {
                // ignore.
            }
        }

        // Runs in main thread (because we need to update mThumbnailView in the
        // main thread)
        public void updateThumbnail() {
            Thumbnail t;
            synchronized (mUpdateThumbnailLock) {
                mHandler.removeMessages(UPDATE_THUMBNAIL);
                t = mPendingThumbnail;
                mPendingThumbnail = null;
            }

            if (t != null) {
                mThumbnail = t;
                mThumbnailView.setBitmap(mThumbnail.getBitmap());
                if (!mIsImageCaptureIntent && !mThumbnail.fromFile()) {
                    mThumbnail.saveTo(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
                }
            }
            // Share popup may still have the reference to the old thumbnail. Clear it.
            mSharePopup = null;
        }

        // Runs in saver thread
        private void storeImage(final byte[] data, Location loc, int width,
                int height, long dateTaken, int previewWidth) {
            String title = Util.createJpegName(dateTaken);

            if(mIsTestExecuting) {
                title = title + "_" + mIMGscriptTitle;
                mIsTestExecuting = false;
            }

            int orientation = Exif.getOrientation(data);
            Uri uri = Storage.addImage(mContentResolver, title, mPictureFormat, dateTaken,
                    loc, orientation, data, width, height);
            if (uri != null) {
                boolean needThumbnail;
                synchronized (this) {
                    // If the number of requests in the queue (include the
                    // current one) is greater than 1, we don't need to generate
                    // thumbnail for this image. Because we'll soon replace it
                    // with the thumbnail for some image later in the queue.
                    needThumbnail = (mQueue.size() <= 1);
                }
                if (needThumbnail) {
                    // Create a thumbnail whose width is equal or bigger than
                    // that of the preview.
                    int ratio = (int) Math.ceil((double) width / previewWidth);
                    int inSampleSize = Integer.highestOneBit(ratio);
                    Thumbnail t = Thumbnail.createThumbnail(
                                data, orientation, inSampleSize, uri);
                    synchronized (mUpdateThumbnailLock) {
                        // We need to update the thumbnail in the main thread,
                        // so send a message to run updateThumbnail().
                        mPendingThumbnail = t;
                        mHandler.sendEmptyMessage(UPDATE_THUMBNAIL);
                    }
                }
                Util.broadcastNewPicture(CPCam.this, uri);
            }
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case SNAPSHOT_IN_PROGRESS:
            case FOCUSING:
                enableCameraControls(false);
                break;
            case IDLE:
            case PREVIEW_STOPPED:
                enableCameraControls(true);
                break;
        }
    }

    private boolean setUpQueuedShot() {
        mCaptureStartTime = System.currentTimeMillis();
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;
        // Set rotation and gps data.
        Util.setRotationParameterCPCam(mParameters, mCameraId, mOrientation);
        Location loc = mLocationManager.getCurrentLocation();
        Util.setGpsParametersCPCam(mParameters, loc);
        mParameters.setPictureFormat(ImageFormat.NV21);
        mParameters.setPictureSize(mFrameWidth, mFrameHeight);
        mCPCamDevice.setParameters(mParameters);
        if(mShotParams == null) {
            //If shot params are not set, put default values
            mShotParams = mCPCamDevice.getParameters();
            mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_EXP_GAIN_PAIRS,
                            DEFAULT_EXPOSURE_GAIN);
               mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_BURST, 1);
        } else {
            mCPCamDevice.setParameters(mShotParams);
        }

        try {
            mCPCamDevice.takePicture(null, null, null, mJpegCallback, mShotParams);
        } catch (RuntimeException e ) {
            e.printStackTrace();
            return false;
        }
        mFaceDetectionStarted = false;
        setCameraState(QUEUED_SHOT_IN_PROGRESS);
        mReprocessButton.setVisibility(View.VISIBLE);
        mExpGainButton.setVisibility(View.VISIBLE);
        mIndicatorControlContainer.showCPCamSliders(true);
        return true;
    }

    @Override
    public boolean capture() {
        synchronized (mCameraStateLock) {
            Log.d(TAG,"Capture()");
            // If we are already in the middle of taking a snapshot then ignore.
            if (mCPCamDevice == null) {
                return false;
            }

            if ( mCameraState == QUEUED_SHOT_IN_PROGRESS ) {
                setCameraState(IDLE);
                mFocusManager.setQueuedShotState(QueuedShotStates.OFF);
                mReprocessButton.setVisibility(View.INVISIBLE);
                mMetaDataIndicator.setVisibility(View.INVISIBLE);
                mExpGainButton.setVisibility(View.INVISIBLE);
                mIndicatorControlContainer.showCPCamSliders(false);
                Message msg = new Message();
                msg.what = RESTART_PREVIEW;
                msg.arg1 = MODE_RESTART;
                mHandler.sendMessage(msg);
                return true;
            }
            return setUpQueuedShot();
        }
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void setTouchParameters() {
        mParameters = mCPCamDevice.getParameters();
        mCPCamDevice.setParameters(mParameters);
    }

    @Override
    public void playSound(int soundId) {
        mCameraSound.play(soundId);
    }

    private boolean saveDataToFile(String filePath, byte[] data) {
        FileOutputStream f = null;
        try {
            f = new FileOutputStream(filePath);
            f.write(data);
        } catch (IOException e) {
            return false;
        } finally {
            Util.closeSilently(f);
        }
        return true;
    }

    private void getPreferredCameraId() {
        mPreferences = new ComboPreferences(this);
        CPCameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CPCameraSettings.readPreferredCameraId(mPreferences);

        // Testing purpose. Launch a specific camera through the intent extras.
        int intentCameraId = Util.getCameraFacingIntentExtras(this);
        if (intentCameraId != -1) {
            mCameraId = intentCameraId;
        }
    }

    Thread mCameraOpenThread = new Thread(new Runnable() {
        public void run() {
            try {
                mCPCamDevice = Util.openCPCamera(CPCam.this, mCameraId);
            } catch (CameraHardwareException e) {
                mOpenCameraFail = true;
            } catch (CameraDisabledException e) {
                mCameraDisabled = true;
            }
        }
    });

    Thread mCameraPreviewThread = new Thread(new Runnable() {
        public void run() {
            initializeCapabilities();
            startPreview(true);
        }
    });

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferredCameraId();
        String[] defaultFocusModes = getResources().getStringArray(
                R.array.pref_camera_focusmode_default_array);
        mFocusManager = new CPCamFocusManager(mPreferences, defaultFocusModes);
        mContext = this;
        /*
         * To reduce startup time, we start the camera open and preview threads.
         * We make sure the preview is started at the end of onCreate.
         */
        mCameraOpenThread.start();

        PreferenceInflater inflater = new PreferenceInflater(this);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(R.xml.camera_preferences);

        ListPreference temp = group.findPreference(CPCameraSettings.KEY_MODE_MENU);

        getPreferredCameraId();
        mFocusManager = new CPCamFocusManager(mPreferences,
                defaultFocusModes);
        mTouchManager = new TouchManager();

        mIsImageCaptureIntent = isImageCaptureIntent();
        setContentView(R.layout.cpcamcamera);
        if (mIsImageCaptureIntent) {
            mReviewDoneButton = (Rotatable) findViewById(R.id.btn_done);
            mReviewCancelButton = (Rotatable) findViewById(R.id.btn_cancel);
            findViewById(R.id.btn_cancel).setVisibility(View.VISIBLE);
        } else {
            mThumbnailView = (RotateImageView) findViewById(R.id.thumbnail);
            mThumbnailView.enableFilter(false);
            mThumbnailView.setVisibility(View.VISIBLE);
        }

        mRotateDialog = new RotateDialogController(this, R.layout.rotate_dialog);
        mCaptureLayout = getString(R.string.pref_camera_capture_layout_default);

        mPreferences.setLocalId(this, mCameraId);
        CPCameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);

        Util.enterLightsOutMode(getWindow());

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceChanged / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(this);

        s3dView = new S3DViewWrapper(holder);

        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Make sure camera device is opened.
        try {
            mCameraOpenThread.join();
            mCameraOpenThread = null;
            if (mOpenCameraFail) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } else if (mCameraDisabled) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        mCameraPreviewThread.start();

        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        } else {
            mModePicker = (ModePicker) findViewById(R.id.mode_picker);
            mModePicker.setVisibility(View.VISIBLE);
            mModePicker.setOnModeChangeListener(this);
            mModePicker.setCurrentMode(ModePicker.MODE_CPCAM);
        }

        mCpcamGainControl = (CPcamGainControl) findViewById(R.id.gain_control);
        mCpcamExposureControl = (CPcamExposureControl) findViewById(R.id.exposure_control);
        mOnScreenIndicators = (Rotatable) findViewById(R.id.on_screen_indicators);
        mLocationManager = new LocationManager(this, this);

        mExpGainButton = (Button) findViewById(R.id.manual_gain_exposure_button);
        mExpGainButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if(mIsRelativeExposureGainPair ){
                        mExpGainButton.setText(ABSOLUTE_EXP_GAIN_TEXT);
                        mIsRelativeExposureGainPair = false;
                    } else {
                        mExpGainButton.setText(RELATIVE_EXP_GAIN_TEXT);
                        mIsRelativeExposureGainPair = true;
                    }
                    initializeCPcamSliders(mIsRelativeExposureGainPair);
                }
        });

        mReprocessButton = (Button) findViewById(R.id.reprocess_button);
        mReprocessButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mReprocessNextFrame = true;
                    mRestartQueueShot = true;
                }
        });

        // Wait until the camera settings are retrieved.
        synchronized (mCameraPreviewThread) {
            try {
                mCameraPreviewThread.wait();
            } catch (InterruptedException ex) {
                // ignore
            }
        }
        // Do this after starting preview because it depends on camera
        // parameters.
        initializeIndicatorControl();
        mCameraSound = new MediaActionSound();
        // Make sure preview is started.
        try {
            mCameraPreviewThread.join();
        } catch (InterruptedException ex) {
            // ignore
        }
        mCameraPreviewThread = null;
    }

    private void loadCameraPreferences() {
        CPCameraSettings settings = new CPCameraSettings((Activity)this, mInitialParams,
                mCameraId, CameraHolder.instance().getCameraInfo());
        mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);
    }

    private void initializeIndicatorControl() {
        // setting the indicator buttons.
        mIndicatorControlContainer =
                (IndicatorControlContainer) findViewById(R.id.cpcam_indicator_control);
        if (mIndicatorControlContainer == null) return;
        loadCameraPreferences();

        CameraPicker.setImageResourceId(R.drawable.ic_switch_photo_facing_holo_light);
        mIndicatorControlContainer.initialize(this, mPreferenceGroup,
                false, true, null, null);

        mIndicatorControlContainer.setListener(this);
        mIndicatorControlContainer.dismissSecondLevelIndicator();
    }

    private boolean collapseCameraControls() {
        if ((mIndicatorControlContainer != null)
                && mIndicatorControlContainer.dismissSettingPopup()) {
            return true;
        }
        return false;
    }

    private void enableCameraControls(boolean enable) {
        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.setEnabled(enable);
        }
        if (mModePicker != null) mModePicker.setEnabled(enable);
        if (mCpcamGainControl != null) mCpcamGainControl.setEnabled(enable);
        if (mCpcamExposureControl != null) mCpcamExposureControl.setEnabled(enable);
        if (mThumbnailView != null) mThumbnailView.setEnabled(enable);
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CPCam.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                setOrientationIndicator(mOrientationCompensation);
            }

            // Show the toast after getting the first orientation changed.
            if (mHandler.hasMessages(SHOW_TAP_TO_FOCUS_TOAST)) {
                mHandler.removeMessages(SHOW_TAP_TO_FOCUS_TOAST);
                showTapToFocusToast();
            }
        }
    }

    private void setOrientationIndicator(int orientation) {
        Rotatable[] indicators = {mThumbnailView, mModePicker, mSharePopup,
                mIndicatorControlContainer, null, mCpcamGainControl, mCpcamExposureControl,
                mFocusAreaIndicator, null, mReviewCancelButton, mReviewDoneButton,
                mRotateDialog, mOnScreenIndicators};
        for (Rotatable indicator : indicators) {
            if (indicator != null) indicator.setOrientation(orientation);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    private void checkStorage() {
        mPicturesRemaining = Storage.getAvailableSpace();
        if (mPicturesRemaining > Storage.LOW_STORAGE_THRESHOLD) {
            mPicturesRemaining = (mPicturesRemaining - Storage.LOW_STORAGE_THRESHOLD)
                    / Storage.PICTURE_SIZE;
        } else if (mPicturesRemaining > 0) {
            mPicturesRemaining = 0;
        }

        updateStorageHint();
    }

    @OnClickAttr
    public void onThumbnailClicked(View v) {
        if (isCameraIdle() && mThumbnail != null) {
            showSharePopup();
        }
    }

    @OnClickAttr
    public void onReviewRetakeClicked(View v) {
        hidePostCaptureAlert();
        startPreview(true);
        startFaceDetection();
    }

    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        setResultEx(RESULT_OK);
        finish();
    }

    @OnClickAttr
    public void onReviewCancelClicked(View v) {
        doCancel();
    }

    private void doAttach() {
        if (mPausing) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to it's
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    setResultEx(RESULT_OK);
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    Util.closeSilently(outputStream);
                }
            } else {
                int orientation = Exif.getOrientation(data);
                Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
                bitmap = Util.rotate(bitmap, orientation);
                setResultEx(RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                setResultEx(Activity.RESULT_CANCELED);
                finish();
                return;
            } catch (IOException ex) {
                setResultEx(Activity.RESULT_CANCELED);
                finish();
                return;
            } finally {
                Util.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean("return-data", true);
            }

            Intent cropIntent = new Intent("com.ti.omap4.android.camera.action.CROP");

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            startActivityForResult(cropIntent, CROP_MSG);
        }
    }

    private void doCancel() {
        setResultEx(RESULT_CANCELED, new Intent());
        finish();
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mPausing || collapseCameraControls() || mCameraState == SNAPSHOT_IN_PROGRESS) return;

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) return;

        if (pressed) {
            mFocusManager.onShutterDown();
        } else {
            mFocusManager.onShutterUp();
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mPausing || collapseCameraControls()) return;

        // Do not take the picture if there is not enough storage.
        if (mPicturesRemaining <= 0) {
            Log.i(TAG, "Not enough space or storage not ready. remaining=" + mPicturesRemaining);
            return;
        }

        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if (mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS) {
            return;
        }

        mFocusManager.doSnap();
    }

    @Override
    public void onShutterButtonLongPressed() {
    }

    private OnScreenHint mStorageHint;

    private void updateStorageHint() {
        String noStorageText = null;

        if (mPicturesRemaining == Storage.UNAVAILABLE) {
            noStorageText = getString(R.string.no_storage);
        } else if (mPicturesRemaining == Storage.PREPARING) {
            noStorageText = getString(R.string.preparing_sd);
        } else if (mPicturesRemaining == Storage.UNKNOWN_SIZE) {
            noStorageText = getString(R.string.access_sd_fail);
        } else if (mPicturesRemaining < 1L) {
            noStorageText = getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, noStorageText);
            } else {
                mStorageHint.setText(noStorageText);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void initDefaults() {
        initializeCPcamSliders(mIsRelativeExposureGainPair);

        if ( null == mPictureFormat ) {
            mPictureFormat = getString(R.string.pref_camera_picture_format_default);
        }

        // Preview layout and size
        if (mPausing) {
            mPreviewLayout = null;
            mCaptureLayout = getString(R.string.pref_camera_capture_layout_default);
        }
    }

    @Override
    protected void doOnResume() {
        if (mOpenCameraFail || mCameraDisabled) return;

        mHandler.removeMessages(RELEASE_CAMERA);

        initDefaults();

        mPausing = false;

        mJpegPictureCallbackTime = 0;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED) {
            try {
                if ( null == mCPCamDevice ) {
                    mCPCamDevice = Util.openCPCamera(this, mCameraId);
                }
                initializeCapabilities();
                startPreview(true);
                startFaceDetection();
            } catch (CameraHardwareException e) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } catch (CameraDisabledException e) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        }

        if (mSurfaceHolder != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            } else {
                initializeSecondTime();
            }
        }
        keepScreenOnAwhile();

        if (mCameraState == IDLE) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
        }
        // Dismiss open menu if exists.
        PopupManager.getInstance(this).notifyShowPopup(null);

        try {
            mCPCamDevice.setBufferSource(null, mTapOut);
        } catch (IOException ioe) {
            Log.e(TAG, "Error trying to setBufferSource!");
        }

        if (mCameraSavedState == QUEUED_SHOT_IN_PROGRESS) synchronized (mCameraStateLock) {
            setUpQueuedShot();
        }

    }

    @Override
    protected void onPause() {
        mPausing = true;
        mCameraSavedState = mCameraState;

        // Delay Camera release if
        // burst is still running
            stopPreview();
            closeCamera();

        if (mCameraSound != null) mCameraSound.release();

        resetScreenOn();

        // Clear UI.
        collapseCameraControls();
        if (mSharePopup != null) mSharePopup.dismiss();
        if (mFaceView != null) mFaceView.clear();

        if (mFirstTimeInitialized) {
            mOrientationListener.disable();
            if (mImageSaver != null) {
                mImageSaver.finish();
                mImageSaver = null;
            }
        }

        if (mLocationManager != null) mLocationManager.recordLocation(false);

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages in the event queue.
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mFocusManager.removeMessages();

        super.onPause();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CROP_MSG: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                setResultEx(resultCode, intent);
                finish();

                File path = getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    private boolean canTakePicture() {
        return isCameraIdle() && (mPicturesRemaining > 0);
    }

    @Override
    public boolean autoFocus() {
        synchronized (mCameraStateLock) {
            if ( mCameraState == IDLE ) {
                mFocusStartTime = System.currentTimeMillis();
                try {
                    mCPCamDevice.autoFocus(mAutoFocusCallback);
                } catch ( RuntimeException e ) {
                    e.printStackTrace();
                    return false;
                }
                setCameraState(FOCUSING);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void cancelAutoFocus() {
        mCPCamDevice.cancelAutoFocus();
        setCameraState(IDLE);
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    // Preview area is touched. Handle touch focus and touch convergence
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        if (mPausing || mCPCamDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS) {
            return false;
        }
        if (collapseCameraControls()) return false;

        return true;
    }

    @Override
    public void onBackPressed() {
        if (!isCameraIdle()) {
            // ignore backs while we're taking a picture
            return;
        } else if (!collapseCameraControls()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonFocus(true);
                }
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_P:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, onShutterButtonFocus()
                    // will be called again but it is fine.
                    if (collapseCameraControls()) return true;
                    onShutterButtonFocus(true);
                    if (mShutterButton.isInTouchMode()) {
                        mShutterButton.requestFocusFromTouch();
                    } else {
                        mShutterButton.requestFocus();
                    }
                    mShutterButton.setPressed(true);
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        Log.v(TAG, "surfaceChanged. w=" + w + ". h=" + h);

        // We need to save the holder for later use, even when the mCPCamDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // The mCPCamDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCPCamDevice == null) return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing || isFinishing()) return;

        setSurfaceLayout();

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (mCameraState == PREVIEW_STOPPED) {
            startPreview(true);
            startFaceDetection();
        } else {
            if (Util.getDisplayRotation(this) != mDisplayRotation) {
                setDisplayOrientation();
            }
            if (holder.isCreating()) {
                // Set preview display if the surface is being created and preview
                // was already started. That means preview display was set to null
                // and we need to set it now.
                setPreviewDisplay(holder);
            }
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }

        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        int displayRotation = Util.getDisplayRotation(this);
        int displayOrientation = Util.getDisplayOrientation(displayRotation, mCameraId);

        mTouchManager.initialize(preview.getHeight() / 3, preview.getHeight() / 3,
               preview, this, mirror, displayOrientation);

    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        mSurfaceHolder = null;
    }

    private void setSurfaceLayout() {
        if (mPreviewLayout == null || s3dView == null) {
            return;
        }

        if (!mS3dViewEnabled) {
            s3dView.setMonoLayout();
            return;
        }
        if (mPreviewLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT) ||
            mPreviewLayout.equals(CPCameraSettings.TB_SUB_S3D_LAYOUT)) {
            s3dView.setTopBottomLayout();
        } else if (mPreviewLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT) ||
                   mPreviewLayout.equals(CPCameraSettings.SS_SUB_S3D_LAYOUT)) {
            s3dView.setSideBySideLayout();
        } else {
            s3dView.setMonoLayout();
        }
    }

    private void closeCamera() {
        if (mCPCamDevice != null) {
            CameraHolder.instance().CPCamInstanceRelease();
            mFaceDetectionStarted = false;
            mCPCamDevice.setZoomChangeListener(null);
            mCPCamDevice.setFaceDetectionListener(null);
            mCPCamDevice.setErrorCallback(null);
            mCPCamDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCPCamDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }

    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCPCamDevice.setDisplayOrientation(mDisplayOrientation);
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void startPreview(boolean updateAll) {
        if (mPausing || isFinishing()) return;

        mFocusManager.resetTouchFocus();

        mCPCamDevice.setErrorCallback(mErrorCallback);

        // If we're previewing already, stop the preview first (this will blank
        // the screen).
        if (mCameraState != PREVIEW_STOPPED) stopPreview();

        setPreviewDisplay(mSurfaceHolder);
        setDisplayOrientation();

        // If the focus mode is continuous autofocus, call cancelAutoFocus to
        // resume it because it may have been paused by autoFocus call.
        if (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode())) {
            mCPCamDevice.cancelAutoFocus();
        }
        mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.

        if ( updateAll ) {
            Log.v(TAG, "Updating all parameters!");
            setCameraParameters(UPDATE_PARAM_INITIALIZE | UPDATE_PARAM_PREFERENCE);
        } else {
            setCameraParameters(UPDATE_PARAM_MODE);
        }

        // Inform the mainthread to go on the UI initialization.
        if (mCameraPreviewThread != null) {
            synchronized (mCameraPreviewThread) {
                mCameraPreviewThread.notify();
            }
        }

        try {
            Log.v(TAG, "startPreview ");
            mCPCamDevice.startPreview();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }

        mFocusManager.onPreviewStarted();

        if ( mRestartQueueShot ) {
            mParameters.setPictureFormat(ImageFormat.NV21);
            mParameters.setPictureSize(mFrameWidth, mFrameHeight);
            mCPCamDevice.setParameters(mParameters);
            if(mShotParams == null) {
                //If shot params are not set, put default values
                mShotParams = mCPCamDevice.getParameters();
                mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_EXP_GAIN_PAIRS,
                                DEFAULT_EXPOSURE_GAIN);
                   mShotParams.set(CPCameraSettings.KEY_SHOTPARAMS_BURST, 1);
            } else {
                mCPCamDevice.setParameters(mShotParams);
            }

            mRestartQueueShot = false;
            setCameraState(QUEUED_SHOT_IN_PROGRESS);
            // WA: This should be done on first preview callback.
            //     For some reason callbacks are not called after
            //     the second iteration.
            mHandler.sendEmptyMessageDelayed(QUEUE_NEXT_SHOT, CAMERA_RELEASE_DELAY);

        } else {
            setCameraState(IDLE);
        }

    }

     private void stopPreview() {
        if (mCPCamDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");

            mCPCamDevice.cancelAutoFocus(); // Reset the focus.
            mCPCamDevice.stopPreview();
            mFaceDetectionStarted = false;
        }
        setCameraState(PREVIEW_STOPPED);
        mFocusManager.onPreviewStopped();
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<int[]> frameRates = mParameters.getSupportedPreviewFpsRange();
        if (frameRates != null) {
            int last = frameRates.size() - 1;
            int min = (frameRates.get(last))[mParameters.PREVIEW_FPS_MIN_INDEX];
            int max = (frameRates.get(last))[mParameters.PREVIEW_FPS_MAX_INDEX];
            mParameters.setPreviewFpsRange(min,max);
        }
        mParameters.setRecordingHint(false);
        //Disable IPP (LDCNSF) for reprocess - will be enabled later when Ducati support is added
        mParameters.set(PARM_IPP, PARM_IPP_NONE);
    }

    private boolean is2DMode(){
        if (mParameters == null) mParameters = mCPCamDevice.getParameters();
        String currentPreviewLayout = mParameters.get(CPCameraSettings.KEY_S3D_PRV_FRAME_LAYOUT);
        // if there isn't selected layout  -> 2d mode
        if (currentPreviewLayout == null ||
                currentPreviewLayout.equals("") ||
                currentPreviewLayout.equals("none")) {
            return true;
        } else {
            return false;
        }
    }

    private String elementExists(String[] firstArr, String[] secondArr){
        for (int i = 0; i < firstArr.length; i++) {
            for (int j = 0; j < secondArr.length; j++) {
                if (firstArr[i].equals(secondArr[j])) {
                    return firstArr[i];
                }
            }
        }
        return null;
    }

    private ListPreference getSupportedListPreference(String supportedKey, String menuKey){
        List<String> supported = new ArrayList<String>();
        ListPreference menu = mPreferenceGroup.findPreference(menuKey);
        if (menu == null) return null;
        if (supportedKey != null) {
            String supp = mParameters.get(supportedKey);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
        }
        CPCameraSettings.filterUnsupportedOptions(mPreferenceGroup, menu, supported);
        return menu;
    }

    private boolean updateCameraParametersPreference() {
        boolean restartNeeded = false;
        boolean previewLayoutUpdated = false;
        boolean captureLayoutUpdated = false;

        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }

        String previewLayout = null;
        if (is2DMode()) {
            previewLayout = "none";
        } else {
            previewLayout = mPreferences.getString(
                    CPCameraSettings.KEY_PREVIEW_LAYOUT,
                    getString(R.string.pref_camera_preview_layout_default));
        }
        if (previewLayout!=null && !previewLayout.equals(mPreviewLayout)) {
            if (!mIsPreviewLayoutInit) {
                mInitialParams.set(CPCameraSettings.KEY_S3D_PRV_FRAME_LAYOUT, previewLayout);
                CPCameraSettings settings = new CPCameraSettings(this, mInitialParams,
                        mCameraId, CameraHolder.instance().getCameraInfo());
                mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);
                mIsPreviewLayoutInit = true;
            }
            mParameters.set(CPCameraSettings.KEY_S3D_PRV_FRAME_LAYOUT, previewLayout);
            mPreviewLayout = previewLayout;
            previewLayoutUpdated = true;
            restartNeeded  = true;
        }

        if (!is2DMode()) {
            String s3dViewEnabled = mPreferences.getString(
                    CPCameraSettings.KEY_S3D_MENU,
                    getString(R.string.pref_camera_s3d_default));
            mS3dViewEnabled = s3dViewEnabled.equals("on");
        } else {
            mS3dViewEnabled = false;
        }

        String captureLayout = null;
        if (is2DMode()) {
            captureLayout = "none";
        } else {
            captureLayout = mPreferences.getString(
                CPCameraSettings.KEY_CAPTURE_LAYOUT,
                getString(R.string.pref_camera_capture_layout_default));
        }
        if (captureLayout != null && !mCaptureLayout.equals(captureLayout)) {
            if (!mIsCaptureLayoutInit) {
                mInitialParams.set(CPCameraSettings.KEY_S3D_CAP_FRAME_LAYOUT, captureLayout);
                CPCameraSettings settings = new CPCameraSettings(this, mInitialParams,
                        mCameraId, CameraHolder.instance().getCameraInfo());
                mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);
                mIsCaptureLayoutInit = true;
            }
            mParameters.set(CPCameraSettings.KEY_S3D_CAP_FRAME_LAYOUT, captureLayout);
            mCaptureLayout = captureLayout;
            captureLayoutUpdated = true;
        }

        if ((previewLayoutUpdated || captureLayoutUpdated) && mPreferenceGroup !=null) {
            CPCameraSettings settings = new CPCameraSettings(this, mInitialParams,
                    mCameraId, CameraHolder.instance().getCameraInfo());
             mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);

             // Update preview size UI with the new sizes
             if (previewLayoutUpdated) {
                 ListPreference tbMenuSizes = null;
                 ListPreference ssMenuSizes = null;
                 ListPreference menu2dSizes = getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PREVIEW_SUBSAMPLED_SIZES,
                         CPCameraSettings.KEY_PREVIEW_SIZE_2D);
                 if (!is2DMode()) {
                     tbMenuSizes =  getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PREVIEW_TOPBOTTOM_SIZES,CPCameraSettings.KEY_PREVIEW_SIZES_TB);
                     ssMenuSizes =  getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PREVIEW_SIDEBYSIDE_SIZES,CPCameraSettings.KEY_PREVIEW_SIZES_SS);
                 }
                 ListPreference newPreviewSizes = null;
                 if (mPreviewLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
                     newPreviewSizes = ssMenuSizes;
                 } else if (mPreviewLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
                     newPreviewSizes = tbMenuSizes;
                 } else {
                     newPreviewSizes = menu2dSizes;
                 }
                 ArrayList<CharSequence[]> allEntries = new ArrayList<CharSequence[]>();
                 ArrayList<CharSequence[]> allEntryValues = new ArrayList<CharSequence[]>();
                 allEntries.add(menu2dSizes.getEntries());
                 allEntryValues.add(menu2dSizes.getEntryValues());
                 if (!is2DMode()) {
                     if (tbMenuSizes != null) {
                         allEntries.add(tbMenuSizes.getEntries());
                         allEntryValues.add(tbMenuSizes.getEntryValues());
                     }
                     if (ssMenuSizes != null) {
                         allEntries.add(ssMenuSizes.getEntries());
                         allEntryValues.add(ssMenuSizes.getEntryValues());
                     }
                 }

                 if (mIndicatorControlContainer != null ) {
                     mIndicatorControlContainer.replace(CPCameraSettings.KEY_PREVIEW_SIZE, newPreviewSizes, allEntries, allEntryValues);
                 }
             }

            // Update picture size UI with the new sizes
             if (captureLayoutUpdated) {
                 ListPreference menu2dSizes = getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PICTURE_SUBSAMPLED_SIZES,
                         CPCameraSettings.KEY_PICTURE_SIZE_2D);
                 ListPreference tbMenuSizes = null;
                 ListPreference ssMenuSizes = null;
                 if (!is2DMode()) {
                     tbMenuSizes =  getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PICTURE_TOPBOTTOM_SIZES,CPCameraSettings.KEY_PICTURE_SIZES_TB);
                     ssMenuSizes =  getSupportedListPreference(CPCameraSettings.KEY_SUPPORTED_PICTURE_SIDEBYSIDE_SIZES,CPCameraSettings.KEY_PICTURE_SIZES_SS);
                 }
                 ListPreference newPictureSizes = null;
                 if (mCaptureLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
                     newPictureSizes = ssMenuSizes;
                 } else if (mCaptureLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
                     newPictureSizes = tbMenuSizes;
                 } else {
                     newPictureSizes = menu2dSizes;
                 }
                 ArrayList<CharSequence[]> allEntries = new ArrayList<CharSequence[]>();
                 ArrayList<CharSequence[]> allEntryValues = new ArrayList<CharSequence[]>();
                 if (menu2dSizes != null) {
                     allEntries.add(menu2dSizes.getEntries());
                     allEntryValues.add(menu2dSizes.getEntryValues());
                 }
                 if (!is2DMode()) {
                     if (tbMenuSizes != null) {
                         allEntries.add(tbMenuSizes.getEntries());
                         allEntryValues.add(tbMenuSizes.getEntryValues());
                     }
                     if (ssMenuSizes != null) {
                         allEntries.add(ssMenuSizes.getEntries());
                         allEntryValues.add(ssMenuSizes.getEntryValues());
                     }
                 }

                 if (mIndicatorControlContainer != null ) {
                     mIndicatorControlContainer.replace(CPCameraSettings.KEY_PICTURE_SIZE, newPictureSizes, allEntries, allEntryValues);
                 }
             }
        }

        // Set Default Sensor Orientation
        String sensorOrientation =
           getString(R.string.pref_omap4_camera_sensor_orientation_default);
        mParameters.set(PARM_SENSOR_ORIENTATION, sensorOrientation);

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CPCameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CPCameraSettings.initialCameraPictureSize(this, mParameters);
        } else {
            List<String> supported = new ArrayList<String>();
            String supp = null;
            if (mCaptureLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PICTURE_TOPBOTTOM_SIZES);
                if(supp !=null && !supp.equals("")){
                    for(String item : supp.split(",")){
                        supported.add(item);
                    }
                }
            } else if (mCaptureLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PICTURE_SIDEBYSIDE_SIZES);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
            }else if (mCaptureLayout.equals(CPCameraSettings.SS_SUB_S3D_LAYOUT) ||
                    mCaptureLayout.equals(CPCameraSettings.TB_SUB_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PICTURE_SUBSAMPLED_SIZES);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
            } else {
                supported = CPCameraSettings.sizeListToStringList(mParameters.getSupportedPictureSizes());
            }
             CPCameraSettings.setCameraPictureSize(
                     pictureSize, supported, mParameters);
             mFrameWidth = CPCameraSettings.getCameraPictureSizeWidth(pictureSize);
             mFrameHeight = CPCameraSettings.getCameraPictureSizeHeight(pictureSize);
        }

        mPreviewPanel = findViewById(R.id.frame_layout);
        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);

        String[] previewDefaults = getResources().getStringArray(R.array.pref_camera_previewsize_default_array);
        String defaultPreviewSize = "";
        if (mPreviewLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
            String[] tbPreviewSizes = getResources().getStringArray(R.array.pref_camera_tb_previewsize_entryvalues);
            defaultPreviewSize = elementExists(previewDefaults, tbPreviewSizes);
        } else if (mPreviewLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
            String[] ssPreviewSizes = getResources().getStringArray(R.array.pref_camera_ss_previewsize_entryvalues);
            defaultPreviewSize = elementExists(previewDefaults, ssPreviewSizes);
        } else {
            String[] previewSizes2D = getResources().getStringArray(R.array.pref_camera_previewsize_entryvalues);
            defaultPreviewSize = elementExists(previewDefaults, previewSizes2D);
        }

        String previewSize = mPreferences.getString(CPCameraSettings.KEY_PREVIEW_SIZE, defaultPreviewSize);
        if (previewSize !=null && (!previewSize.equals(mPreviewSize) || previewLayoutUpdated )) {
            List<String> supported = new ArrayList<String>();
            String supp = null;
            if (mPreviewLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PREVIEW_TOPBOTTOM_SIZES);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
            } else if (mPreviewLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PREVIEW_SIDEBYSIDE_SIZES);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
            } else if (mPreviewLayout.equals(CPCameraSettings.SS_SUB_S3D_LAYOUT) ||
                    mPreviewLayout.equals(CPCameraSettings.TB_SUB_S3D_LAYOUT)) {
                supp = mParameters.get(CPCameraSettings.KEY_SUPPORTED_PREVIEW_SUBSAMPLED_SIZES);
                if (supp !=null && !supp.equals("")) {
                    for (String item : supp.split(",")) {
                        supported.add(item);
                    }
                }
            } else {
                supported = CPCameraSettings.sizeListToStringList(mParameters.getSupportedPreviewSizes());
            }
            CPCameraSettings.setCameraPreviewSize(previewSize, supported, mParameters);
            mPreviewSize = previewSize;
            enableCameraControls(true);
            restartNeeded = true;
        }
        Size size = mParameters.getPreviewSize();
        if (mS3dViewEnabled) {
            if (mPreviewLayout.equals(CPCameraSettings.TB_FULL_S3D_LAYOUT)) {
                size.height /= 2;
            } else if (mPreviewLayout.equals(CPCameraSettings.SS_FULL_S3D_LAYOUT)) {
                size.width /= 2;
            }
        }
        mPreviewFrameLayout.setAspectRatio((double) size.width / size.height);
        mParameters.set(CameraSettings.KEY_MODE, mCaptureMode);

        if (!restartNeeded) {
            setSurfaceLayout();
        }

        return restartNeeded;
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        boolean restartPreview = false;

        mParameters = mCPCamDevice.getParameters();

        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            restartPreview = updateCameraParametersPreference();
        }

        if ((updateSet & UPDATE_PARAM_MODE ) != 0 ) {
            updateCameraParametersPreference();
            Log.v(TAG,"Capture mode set: " + mParameters.get(CPCameraSettings.KEY_MODE));

        }

        mCPCamDevice.setParameters(mParameters);

        if ( ( restartPreview ) && ( mCameraState != PREVIEW_STOPPED ) ) {
            // This will only restart the preview
            // without trying to apply any new
            // camera parameters.
            Message msg = new Message();
            msg.what = RESTART_PREVIEW;
            msg.arg1 = MODE_RESTART;
            mHandler.sendMessage(msg);
        }
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCPCamDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    private void gotoGallery() {
        MenuHelper.gotoCameraImageGallery(this);
    }

    private boolean isCameraIdle() {
        return (mCameraState == IDLE) || (mFocusManager.isFocusCompleted());
    }

    private boolean isImageCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void showPostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            Util.fadeOut(mIndicatorControlContainer);
            Util.fadeOut(mShutterButton);

            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                Util.fadeIn(findViewById(id));
            }
        }
    }

    private void hidePostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                Util.fadeOut(findViewById(id));
            }

            Util.fadeIn(mShutterButton);
            Util.fadeIn(mIndicatorControlContainer);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Only show the menu when camera is idle.
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(isCameraIdle());
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsImageCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
        }
        return true;
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_CAMERA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_CAMERA);
            }
        });
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_VIDEO, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_VIDEO);
            }
        });
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_PANORAMA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_PANORAMA);
            }
        });


        if (mNumberOfCameras > 1) {
            menu.add(R.string.switch_camera_id)
                    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    CPCameraSettings.writePreferredCameraId(mPreferences,
                            (((mCameraId + 1) < mNumberOfCameras)
                            ? (mCameraId + 1) : 0));
                    onSharedPreferenceChanged();
                    return true;
                }
            }).setIcon(android.R.drawable.ic_menu_camera);
        }
    }

    private boolean switchToOtherMode(int mode) {
        if (isFinishing()) return false;
        if (mImageSaver != null) mImageSaver.waitDone();
        MenuHelper.gotoMode(mode, CPCam.this);
        mHandler.removeMessages(FIRST_TIME_INIT);
        finish();
        return true;
    }

    public boolean onModeChanged(int mode) {
        if (mode != ModePicker.MODE_CPCAM) {
            return switchToOtherMode(mode);
        } else {
            return true;
        }
    }

    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPausing) return;

        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        mLocationManager.recordLocation(recordLocation);

        setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);

    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    public void onRestorePreferencesClicked() {
        if (mPausing) return;
        Runnable runnable = new Runnable() {
            public void run() {
                restorePreferences();
            }
        };
        mRotateDialog.showAlertDialog(
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                getString(android.R.string.ok), runnable,
                getString(android.R.string.cancel), null);
    }

    private void restorePreferences() {
        initializeCPcamSliders(mIsRelativeExposureGainPair);

        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.dismissSettingPopup();

            CPCameraSettings.restorePreferences(CPCam.this, mPreferences,
                    mParameters);
            mIndicatorControlContainer.reloadPreferences();
            onSharedPreferenceChanged();
        }
    }

    public void onOverriddenPreferencesClicked() {
        if (mPausing) return;
        if (mNotSelectableToast == null) {
            String str = getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = Toast.makeText(CPCam.this, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    private void showSharePopup() {
        mImageSaver.waitDone();
        Uri uri = mThumbnail.getUri();
        if (mSharePopup == null || !uri.equals(mSharePopup.getUri())) {
            // SharePopup window takes the mPreviewPanel as its size reference.
            mSharePopup = new SharePopup(this, uri, mThumbnail.getBitmap(),
                    mOrientationCompensation, mPreviewPanel);
        }
        mSharePopup.showAtLocation(mThumbnailView, Gravity.NO_GRAVITY, 0, 0);
    }

    @Override
    public void onFaceDetection(Face[] faces, com.ti.omap.android.cpcam.CPCam camera) {
        FaceViewData faceData[] = new FaceViewData[faces.length];

        int i = 0;
        for ( Face face : faces ) {

            faceData[i] = new FaceViewData();
            if ( null == faceData[i] ) {
                break;
            }

            faceData[i].id = face.id;
            faceData[i].leftEye = face.leftEye;
            faceData[i].mouth = face.mouth;
            faceData[i].rect = face.rect;
            faceData[i].rightEye = face.rightEye;
            faceData[i].score = face.score;
            i++;
        }

        mFaceView.setFaces(faceData);
    }

    private void showTapToFocusToast() {
        new RotateTextToast(this, R.string.tap_to_focus, mOrientation).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CPCameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void initializeCapabilities() {
        mInitialParams = mCPCamDevice.getParameters();
        mFocusManager.initializeParameters(mInitialParams);
        mFocusAreaSupported = (mInitialParams.getMaxNumFocusAreas() > 0
                && isSupported(Parameters.FOCUS_MODE_AUTO,
                        mInitialParams.getSupportedFocusModes()));
    }

    public void onFrameAvailable(final CPCamBufferQueue bq) {
        // Invoked every time there's a new frame available in SurfaceTexture
        new Thread(new Runnable() {
            public void run() {
                Log.v(TAG, "onFrameAvailable: SurfaceTexture got updated, Tid: " + Process.myTid());

                if (!mReprocessNextFrame &&
                    ( mCameraState == QUEUED_SHOT_IN_PROGRESS ) ) {
                    // Queue next shot
                    final int slot = bq.acquireBuffer();
                    mHandler.post( new Runnable()  {
                        @Override
                        public void run() {
                            updateOnScreenMetadataIndicators(CPCamMetadata.getMetadata(bq, slot));
                        }
                    });

                    Message msg = new Message();
                    msg.what = QUEUE_NEXT_SHOT;
                    mHandler.sendMessage(msg);
                    bq.releaseBuffer(slot);
                } else if ( mReprocessNextFrame &&
                           ( mCameraState == QUEUED_SHOT_IN_PROGRESS ) ) {
                    // Reprocess
                    try {
                        mParameters.setPictureFormat(ImageFormat.JPEG);
                        mParameters.setPictureSize(mFrameWidth, mFrameHeight);
                        mCPCamDevice.setParameters(mParameters);
                        mTapOut.setDefaultBufferSize(mFrameWidth, mFrameHeight);
                        mCPCamDevice.setBufferSource(mTapOut,null);
                        mCPCamDevice.reprocess(mShotParams);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        }).start();
    }
}