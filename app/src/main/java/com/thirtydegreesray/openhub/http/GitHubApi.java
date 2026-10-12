package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.http.core.AppRetrofit;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Small helpers for code that talks to the GitHub API outside a presenter, usually in
 * a sequence of dependent calls on a background thread (save a release, then delete
 * and rename its assets, ...).
 */
public final class GitHubApi {

    private GitHubApi() {
    }

    /** A failed API call, with GitHub's own explanation as the message. */
    public static class ApiException extends IOException {
        public final int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    @NonNull
    public static <T> T service(@NonNull Class<T> serviceClass) {
        return AppRetrofit.INSTANCE
                .getRetrofit(AppConfig.GITHUB_API_BASE_URL, AppData.INSTANCE.getAccessToken())
                .create(serviceClass);
    }

    /**
     * Runs the call on the current thread (never the main thread) and returns the body.
     *
     * @throws ApiException for a non-2xx answer
     */
    @Nullable
    public static <T> T call(@NonNull Observable<Response<T>> observable) throws IOException {
        Response<T> response;
        try {
            response = observable.blockingFirst();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw e;
        }
        if (!response.isSuccessful()) {
            throw new ApiException(response.code(), errorMessage(response));
        }
        return response.body();
    }

    /** Readable text for a failed response: "Validation Failed: tag_name already exists". */
    @NonNull
    public static String errorMessage(@NonNull Response<?> response) {
        String text = null;
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) text = errorMessage(errorBody.string());
        } catch (Exception ignored) {
        }
        if (StringUtils.isBlank(text)) {
            text = StringUtils.isBlank(response.message()) ? "" : response.message();
        }
        return "HTTP " + response.code() + (StringUtils.isBlank(text) ? "" : ": " + text);
    }

    @Nullable
    public static String errorMessage(@Nullable String json) {
        if (StringUtils.isBlank(json)) return null;
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            StringBuilder builder = new StringBuilder();
            if (object.has("message") && !object.get("message").isJsonNull()) {
                builder.append(object.get("message").getAsString());
            }
            if (object.has("errors") && object.get("errors").isJsonArray()) {
                JsonArray errors = object.getAsJsonArray("errors");
                boolean first = true;
                for (JsonElement element : errors) {
                    String detail = describe(element);
                    if (StringUtils.isBlank(detail)) continue;
                    if (builder.length() > 0) builder.append(first ? ": " : "; ");
                    builder.append(detail);
                    first = false;
                }
            }
            return builder.length() == 0 ? null : builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String describe(@NonNull JsonElement element) {
        if (element.isJsonPrimitive()) return element.getAsString();
        if (!element.isJsonObject()) return null;
        JsonObject error = element.getAsJsonObject();
        String message = string(error, "message");
        if (!StringUtils.isBlank(message)) return message;
        String field = string(error, "field");
        String code = string(error, "code");
        if (StringUtils.isBlank(code)) return field;
        code = code.replace('_', ' ');
        return StringUtils.isBlank(field) ? code : field + " " + code;
    }

    @Nullable
    private static String string(@NonNull JsonObject object, @NonNull String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }
}
