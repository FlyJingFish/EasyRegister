package com.flyjingfish.easy_register;

import android.app.Application;
import android.util.Log;

import com.flyjingfish.easy_register.base.InitCollect;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("MyApp","=====>>>>wwwsssww");
        InitCollect.testEasyRegister(this);
    }

}
