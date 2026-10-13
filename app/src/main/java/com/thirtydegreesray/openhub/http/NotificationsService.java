

package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.Notification;
import com.thirtydegreesray.openhub.mvp.model.request.MarkNotificationReadRequestModel;

import java.util.ArrayList;

import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/11/6 21:03:04
 */

public interface NotificationsService {

    @NonNull @GET("notifications?per_page=50")
    Observable<Response<ArrayList<Notification>>> getMyNotifications(
            @Header("forceNetWork") boolean forceNetWork,
            @Query("all") boolean all,
            @Query("participating") boolean participating,
            @Query("page") int page
    );

    /** "Done" on github.com: the thread leaves the inbox until something new happens. */
    @NonNull @DELETE("notifications/threads/{threadId}")
    Observable<Response<ResponseBody>> markNotificationAsDone(
            @Path("threadId") String threadId
    );

    /** Stop notifications for this issue or pull request. */
    @NonNull @DELETE("notifications/threads/{threadId}/subscription")
    Observable<Response<ResponseBody>> unsubscribeThread(
            @Path("threadId") String threadId
    );

    @NonNull @PATCH("notifications/threads/{threadId}")
    Observable<Response<ResponseBody>> markNotificationAsRead(
            @Path("threadId") String threadId
    );

    @NonNull @PUT("notifications")
    Observable<Response<ResponseBody>> markAllNotificationsAsRead(
            @Body MarkNotificationReadRequestModel notificationRequestModel
    );

    @NonNull @PUT("repos/{owner}/{repo}/notifications")
    Observable<Response<ResponseBody>> markRepoNotificationsAsRead(
            @Body MarkNotificationReadRequestModel notificationRequestModel,
            @Path("owner") String owner,
            @Path("repo") String repo
    );

}
