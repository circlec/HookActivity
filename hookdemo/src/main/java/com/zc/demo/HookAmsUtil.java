package com.zc.demo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class HookAmsUtil {
    private Context mContext;
    private Class<?> proxyActivity;

    public HookAmsUtil(Class<?> proxyActivity, Context mContext) {
        this.mContext = mContext;
        this.proxyActivity = proxyActivity;
    }

    /**
     * 在验证清单文件时 用代理activity验证
     *
     * @throws Exception
     */
    public void hookAms() throws Exception {
        Class<?> ActivityManagerNativeClss = Class.forName("android.app.ActivityManagerNative");
        Field defaultFiled = ActivityManagerNativeClss.getDeclaredField("gDefault");
        defaultFiled.setAccessible(true);
        Object defaultValue = defaultFiled.get(null);
        //反射SingleTon
        Class<?> SingletonClass = Class.forName("android.util.Singleton");
        Field mInstance = SingletonClass.getDeclaredField("mInstance");
        mInstance.setAccessible(true);
        //到这里已经拿到ActivityManager对象
        Object iActivityManagerObject = mInstance.get(defaultValue);
        //开始动态代理，用代理对象替换掉真实的ActivityManager
        Class<?> IActivityManagerIntercept = Class.forName("android.app.IActivityManager");

        AmsInvocationHandler handler = new AmsInvocationHandler(iActivityManagerObject);

        Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{IActivityManagerIntercept}, handler);

        //现在替换掉这个对象
        mInstance.set(defaultValue, proxy);
    }

    /**
     * 验证完成后启动activity时 用需要打开的activity替换代理activity
     */
    public void hookSystemHandler() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            //获取主线程对象
            Object activityThread = currentActivityThreadMethod.invoke(null);
            //获取mH字段
            Field mH = activityThreadClass.getDeclaredField("mH");
            mH.setAccessible(true);
            //获取Handler
            Handler handler = (Handler) mH.get(activityThread);
            //获取原始的mCallBack字段
            Field mCallBack = Handler.class.getDeclaredField("mCallback");
            mCallBack.setAccessible(true);
            //这里设置了我们自己实现了接口的CallBack对象
            mCallBack.set(handler, new ActivityThreadHandlerCallback(handler)) ;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class ActivityThreadHandlerCallback implements Handler.Callback {

        private Handler handler;

        private ActivityThreadHandlerCallback(Handler handler) {
            this.handler = handler;
        }

        @Override
        public boolean handleMessage(Message msg) {
            //替换之前的Intent
            if (msg.what ==100) {//100是lauchactivity的msg what
                handleLauchActivity(msg);
            }
            handler.handleMessage(msg);
            return true;
        }

        private void handleLauchActivity(Message msg) {
            Object obj = msg.obj;//ActivityClientRecord
            try{
                Field intentField = obj.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                Intent proxyInent = (Intent) intentField.get(obj);
                Intent realIntent = proxyInent.getParcelableExtra("oldIntent");
                if (realIntent != null) {
                    proxyInent.setComponent(realIntent.getComponent());
                }
            }catch (Exception e){
                Log.i("HookAmsUtil","lauchActivity falied");
            }

        }
    }

    private class AmsInvocationHandler implements InvocationHandler {

        private Object iActivityManagerObject;

        private AmsInvocationHandler(Object iActivityManagerObject) {
            this.iActivityManagerObject = iActivityManagerObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("startActivity".contains(method.getName())) {
                //换掉
                Intent intent = null;
                int index = 0;
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof Intent) {
                        //说明找到了startActivity的Intent参数
                        intent = (Intent) args[i];
                        //这个意图是不能被启动的，因为Acitivity没有在清单文件中注册
                        index = i;
                    }
                }

                //伪造一个代理的Intent，代理Intent启动的是proxyActivity
                Intent proxyIntent = new Intent();
                ComponentName componentName = new ComponentName(mContext, proxyActivity);
                proxyIntent.setComponent(componentName);
                proxyIntent.putExtra("oldIntent", intent);
                args[index] = proxyIntent;
            }

            return method.invoke(iActivityManagerObject, args);
        }
    }
}
