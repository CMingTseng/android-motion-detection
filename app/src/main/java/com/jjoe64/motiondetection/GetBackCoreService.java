package com.jjoe64.motiondetection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class GetBackCoreService extends Service implements CaptureActionCallback {
    private Looper myLooper = Looper.myLooper();
    private static boolean isModeActive = false;

    private static class ActionLocks {
        public AtomicBoolean lockCapture;
        public AtomicBoolean lockSmsSend;
        public AtomicBoolean lockEmailSend;
        public AtomicBoolean lockLocationFind;
        public AtomicBoolean lockDataDelete;

        public ActionLocks() {
            lockCapture = new AtomicBoolean(false);
            lockSmsSend = new AtomicBoolean(false);
            lockEmailSend = new AtomicBoolean(false);
            lockLocationFind = new AtomicBoolean(false);
            lockDataDelete = new AtomicBoolean(false);
        }
    }

    private static ActionLocks actionLocks = null;

    private SharedPreferences preferences;
    private String photoPath = null;
    private static GetBackStateFlags stateFlags = new GetBackStateFlags();
    private static GetBackFeatures features = new GetBackFeatures();

    public GetBackCoreService() {
        super();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("GetBackCoreService", "Show Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
        //  Toast.makeText(this,"",Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //if more than 26
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                String CHANNEL_ONE_ID = ".com.example.service";
                String CHANNEL_ONE_NAME = "hidden camera";
                NotificationChannel notificationChannel = null;
                notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                        CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(notificationChannel);
                }

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("Service")
                        .setContentText("Capture picture using foreground service")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(icon)
                        .build();

                Intent notificationIntent = new Intent(getApplicationContext(), GetBackCoreService.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                startForeground(104, notification);
            } else {
                //if version 26
                startForeground(104, updateNotification());
            }
        }else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("text")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(true)
                    .setAutoCancel(true);

            Notification notification = builder.build();

            startForeground(1, notification);
        }
        takeAction(null);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
         super.onDestroy();
        Log.d("GetBackCoreService", "onDestroy ");
//        Utils.LogUtil.LogD(Constants.LOG_TAG, "Service Destroyed");

        stopSelf();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPhotoCaptured(String filePath) {
        synchronized (stateFlags) {
            Log.d("GetBackCoreService", "onPhotoCaptured  filePath : "+filePath);
            Toast.makeText(this, "onphotocaputred", Toast.LENGTH_LONG).show();
//            stateFlags.isPhotoCaptured = true;
//            addBooleanPreference(Constants.PREFERENCE_IS_PHOTO_CAPTURED,
//                    stateFlags.isPhotoCaptured);
//
//            Utils.LogUtil.LogD(Constants.LOG_TAG, "Image saved at - "
//                    + filePath);
//            photoPath = filePath;
//            addStringPreference(Constants.PREFERENCE_PHOTO_PATH, photoPath);
        }

        actionLocks.lockCapture.set(false);
        takeAction(null);
    }

    private void addBooleanPreference(String key, boolean value) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putBoolean(key, value);
        edit.commit();
    }

    private void addStringPreference(String key, String value) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(key, value);
        edit.commit();
    }

    private synchronized void takeAction(Bundle bundle) {
        capturePhoto();
    }

    private void capturePhoto() {
        Log.d("fuck", "k");
        new Thread(new Runnable() {
            @Override
            public void run() {
//                Utils.LogUtil.LogD(Constants.LOG_TAG,
//                        "Inside captureThread run");

                myLooper.prepare();

                // Check if phone is being used.
                CameraView frontCapture = new CameraView( GetBackCoreService.this.getBaseContext());
                frontCapture.capturePhoto( GetBackCoreService.this);

                myLooper.loop();
            }
        }).start();
    }


    @Override
    public void onCaptureError(int errorCode) {

    }

    private Notification updateNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, GetBackCoreService.class), 0);

        return new NotificationCompat.Builder(this)
                .setTicker("Ticker")
                .setContentTitle("Service")
                .setContentText("Capture picture using foreground service")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
    }
}