package com.vypeensoft.ftpserver;

import android.app.Application;

public class FTPApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.init();
    }
}
