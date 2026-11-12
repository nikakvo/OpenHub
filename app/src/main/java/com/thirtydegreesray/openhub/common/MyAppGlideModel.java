

package com.thirtydegreesray.openhub.common;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.http.core.MediaHttp;

import java.io.InputStream;

/**
 * Created by ThirtyDegreesRay on 2017/8/26 22:47:20
 */
@GlideModule
public class MyAppGlideModel extends AppGlideModule {

    /** No old manifest modules: everything is registered here. */
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        super.applyOptions(context, builder);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, AppConfig.IMAGE_MAX_CACHE_SIZE));
        // no default placeholder: an avatar without a picture (loading, failed, held back on
        // mobile data) draws its own neutral person (CircleImageView); the app logo there
        // looked like a strange avatar, and was huge in the blurred profile header

    }

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        super.registerComponents(context, glide, registry);
        // pictures are loaded with OkHttp (timeouts, HTTP/2, the same network path as the
        // rest of the app) instead of Glide's HttpURLConnection loader
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(MediaHttp.client()));
    }

}
