

package com.thirtydegreesray.openhub;

import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerAppComponent;
import com.thirtydegreesray.openhub.inject.module.AppModule;
import com.thirtydegreesray.openhub.service.NetBroadcastReceiver;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.NetHelper;

/**
 * AppApplication
 * Created by ThirtyDegreesRay on 2016/7/13 14:01
 */
public class AppApplication extends Application {

    private final String TAG = AppApplication.class.getSimpleName();

    private static AppApplication application;
    private AppComponent mAppComponent;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        //init application
        long startTime = System.currentTimeMillis();
        AppData.INSTANCE.getSystemDefaultLocal();
        //apply language for application context
        AppUtils.updateAppLanguage(getApplicationContext());
        initLogger();
        Logger.t(TAG).i("startTime:" + startTime);
        mAppComponent = DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .build();
        initNetwork();
        // RxJava 3 sends errors that arrive after a request was cancelled (screen closed)
        // to this handler; without one they would crash the app.
        RxJavaPlugins.setErrorHandler(error -> Logger.t(TAG).w("Rx error after dispose: " + error));
        // keep the release check scheduled (no-op when nothing is watched)
        ReleaseWatcher.schedule(this);
        com.thirtydegreesray.openhub.util.GitHubNotifier.schedule(this);
        com.thirtydegreesray.openhub.util.ActionsWatcher.schedule(this);
        startTime = System.currentTimeMillis();
        Logger.t(TAG).i("application ok:" + (System.currentTimeMillis() - startTime));


    }

    private void initLogger(){
        PrettyFormatStrategy strategy = PrettyFormatStrategy.newBuilder()
                        .showThreadInfo(false)
                        .methodCount(0)
                        .methodOffset(0)
                        .tag("OpenHub_Logger")
                        .build();
        Logger.addLogAdapter(new AndroidLogAdapter(strategy){
            @Override
            public boolean isLoggable(int priority, String tag) {
                return BuildConfig.DEBUG;
            }

            @Override
            public void log(int priority, String tag, String message) {
                super.log(priority, tag, message);
            }
        });
    }

    private void initNetwork(){
        // Android 7+ uses a default-network callback inside NetHelper. The broadcast is
        // only a fallback for older versions.
        NetHelper.INSTANCE.init(this);
        if (NetHelper.INSTANCE.needsBroadcastReceiver()) {
            registerReceiver(new NetBroadcastReceiver(),
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    public static AppApplication get(){
        return application;
    }

    public AppComponent getAppComponent(){
        return mAppComponent;
    }


}
