package com.techlads.screenrecorder;


import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenRecorderService extends Service {

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private static final int REQUEST_CODE = 1000;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private boolean mRecordingStatus = false;
    private int mScreenDensity;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 720;
    private static final int NOTIFICATION_ID = 12345678;
    private int resultCode;
    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);

    }

    private String mVideoUrl = "";

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();




        mRecordingStatus = false;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mScreenDensity = displayMetrics.densityDpi;
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaRecorder = new MediaRecorder();

        //mMediaProjectionCallback = new MediaProjectionCallback();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setAction("stop");
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "001";
            String channelName = "myChannel";
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE);
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
                Notification notification = new Notification.
                        Builder(getApplicationContext(), channelId)
                        .setOngoing(true)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .setContentTitle("Click here")
                        .setContentIntent(contentIntent)
                        .build();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(0, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                } else {
                    startForeground(0, notification);
                }
            }
        } else {
            startForeground(0, new Notification());
        }

        try{
            initRecorder();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            initMediaProjection(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }



        /*if (!mRecordingStatus)
            initRecorder();
            startRecording();*/

        return START_STICKY;

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initMediaProjection(final Intent intent) {

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final int resultCode = intent.getIntExtra("resultCode", 0);
                Intent resultData = intent.getParcelableExtra("data");
                if (isMyServiceRunning(ScreenRecorderService.class))
                mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, resultData);
                if (mMediaProjection != null) {
                    Log.d("projection::", "not null");
                } else {
                    Log.d("projection::", "is null");
                }
            }
        }, 1000);

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initRecorder() {
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

            mVideoUrl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    new StringBuilder("/TechLadRecord-").append(new SimpleDateFormat("dd-MM-yyy-hh_mm_ss")
                            .format(new Date())).append(".mp4").toString();

            mMediaRecorder.setOutputFile(mVideoUrl);
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mMediaRecorder.setVideoFrameRate(30);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATION.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecording() {

        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenRecorderService", DISPLAY_WIDTH, DISPLAY_HEIGHT,
                mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null, null);
    }

    public WindowManager getWindowManager() {
        return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public class MediaProjectionCallback extends MediaProjection.Callback {

        @Override
        public void onStop() {
            super.onStop();

            if (mMediaRecorder != null){
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            }

            mMediaProjection = null;
            stopRecordScreen();
        }

        private void stopRecordScreen() {
            mMediaRecorder.stop();
        }
    }
}