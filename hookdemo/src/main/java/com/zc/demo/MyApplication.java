package com.zc.demo;

import android.app.Application;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            HookAmsUtil hookAmsUtil = new HookAmsUtil(ProxyActivity.class, this);
            hookAmsUtil.hookSystemHandler();
            hookAmsUtil.hookAms();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
