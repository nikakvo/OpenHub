

package com.thirtydegreesray.openhub.inject.module;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.dao.DaoMaster;
import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.dao.RoomGreenDaoDatabase;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * AppModule
 * Created by ThirtyDegreesRay on 2016/8/30 13:52
 */
@Module
public class AppModule {

    private AppApplication application;

    public AppModule(AppApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public AppApplication provideApplication() {
        return application;
    }

    @NonNull
    @Provides
    @Singleton
    public AppDatabase provideDatabase() {
        return AppDatabase.open(application);
    }

    /**
     * greenDAO on Room's connection (Room owns and migrates the database file). Only until
     * all screens use Room.
     */
    @NonNull
    @Provides
    @Singleton
    public DaoSession provideDaoSession(AppDatabase database) {
        DaoMaster daoMaster = new DaoMaster(
                new RoomGreenDaoDatabase(database.getOpenHelper().getWritableDatabase()));
        return daoMaster.newSession();
    }


}
