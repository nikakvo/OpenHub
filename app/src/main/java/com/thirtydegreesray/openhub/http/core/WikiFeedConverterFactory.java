package com.thirtydegreesray.openhub.http.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.model.WikiFeedModel;
import com.thirtydegreesray.openhub.util.WikiFeed;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/** Retrofit converter for the wiki Atom feed of github.com pages (see {@link WikiFeed}). */
public final class WikiFeedConverterFactory extends Converter.Factory {

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(@NonNull Type type,
            @NonNull Annotation[] annotations, @NonNull Retrofit retrofit) {
        if (type != WikiFeedModel.class) return null;
        return (Converter<ResponseBody, WikiFeedModel>) body -> {
            try {
                return WikiFeed.parse(body.string());
            } finally {
                body.close();
            }
        };
    }
}
