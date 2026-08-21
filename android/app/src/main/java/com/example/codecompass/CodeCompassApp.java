package com.example.codecompass;

import android.app.Application;

public class CodeCompassApp extends Application {

    private static CodeCompassApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static CodeCompassApp getInstance() {
        return instance;
    }
}
