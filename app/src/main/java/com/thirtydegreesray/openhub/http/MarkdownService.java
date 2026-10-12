package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** GitHub's own Markdown renderer, so a preview looks exactly like the published text. */
public interface MarkdownService {

    /**
     * Body: text, mode "gfm" and context "owner/repo" (turns #12, @user and commit
     * hashes into links like on github.com). Answers HTML.
     */
    @NonNull @POST("markdown")
    Observable<Response<ResponseBody>> render(@Body Map<String, Object> body);
}
