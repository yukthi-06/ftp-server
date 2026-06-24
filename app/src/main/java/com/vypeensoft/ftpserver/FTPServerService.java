package com.vypeensoft.ftpserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class FTPServerService extends Service {
    private static final String TAG = "FTPServerService";

    public static final String ACTION_START = "com.vypeensoft.ftpserver.ACTION_START";
    public static final String ACTION_STOP = "com.vypeensoft.ftpserver.ACTION_STOP";
    
    private static final String CHANNEL_ID = "FTPServerChannel";
    private static final int NOTIFICATION_ID = 1001;

    private boolean mIsRunning = false;
    private final FTPServerManager mFtpServerManager = FTPServerManager.getInstance();

    private final FTPServerManager.ClientCountListener mClientListener = new FTPServerManager.ClientCountListener() {
        @Override
        public void onClientCountChanged(int count) {
            if (mIsRunning) {
                updateNotification();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mFtpServerManager.setClientCountListener(mClientListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startFtpServer();
            } else if (ACTION_STOP.equals(action)) {
                stopFtpServer();
            }
        }
        return START_STICKY;
    }

    private void startFtpServer() {
        if (mIsRunning) return;

        SettingsManager.Settings settings = SettingsManager.loadSettings();
        try {
            // Check if port is available
            if (!NetworkUtils.isPortAvailable(settings.port)) {
                Log.e(TAG, "Port " + settings.port + " is already in use.");
                stopSelf();
                return;
            }

            mFtpServerManager.startServer(settings);
            mIsRunning = true;

            // Start foreground
            Notification notification = buildNotification(settings);
            startForeground(NOTIFICATION_ID, notification);

            // Update setting state to enabled
            settings.server_enabled = true;
            SettingsManager.saveSettings(settings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start FTP server: " + e.getMessage());
            stopSelf();
        }
    }

    private void stopFtpServer() {
        if (!mIsRunning) return;

        mFtpServerManager.stopServer();
        mIsRunning = false;

        // Update setting state to disabled
        SettingsManager.Settings settings = SettingsManager.loadSettings();
        settings.server_enabled = false;
        SettingsManager.saveSettings(settings);

        stopForeground(true);
        stopSelf();
    }

    private void updateNotification() {
        SettingsManager.Settings settings = SettingsManager.loadSettings();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(settings));
        }
    }

    private Notification buildNotification(SettingsManager.Settings settings) {
        String ip = NetworkUtils.getWifiIpAddress(this);
        String ftpUrl = NetworkUtils.generateFtpUrl(ip, settings.port);
        int clients = mFtpServerManager.getConnectedClientsCount();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, FTPServerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String contentText = ftpUrl + "\nConnected Clients: " + clients;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FTP Server Running")
                .setContentText(ftpUrl)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "FTP Server Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mFtpServerManager.setClientCountListener(null);
        mFtpServerManager.stopServer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
