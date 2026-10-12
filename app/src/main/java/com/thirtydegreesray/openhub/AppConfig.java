

package com.thirtydegreesray.openhub;

import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Created on 2016/11/23.
 *
 * @author ThirtyDegreesRay
 */

public class AppConfig {

    public final static String GITHUB_BASE_URL = "https://github.com/";

    public final static String GITHUB_API_BASE_URL = "https://api.github.com/";

    public final static String GITHUB_CONTENT_BASE_URL = "https://raw.githubusercontent.com/";

    public final static int HTTP_TIME_OUT = 32 * 1000;

    public final static int HTTP_MAX_CACHE_SIZE = 32 * 1024 * 1024;

    public final static int IMAGE_MAX_CACHE_SIZE = 32 * 1024 * 1024;

    public final static int CACHE_MAX_AGE = 4 * 7 * 24 * 60 * 60;

    public final static String DB_NAME = "OpenHub.db";



    public final static String OPENHUB_CLIENT_ID = BuildConfig.OPENHUB_CLIENT_ID;

    public final static String OPENHUB_CLIENT_SECRET = BuildConfig.OPENHUB_CLIENT_SECRET;

    /** workflow: needed to create or change files under .github/workflows */
    public final static String OAUTH2_SCOPE = "user,repo,workflow,gist,notifications";


    public final static String OAUTH2_URL = GITHUB_BASE_URL + "login/oauth/authorize";

    public final static String OPENHUB_HOME = GITHUB_BASE_URL + "nikakvo/OpenHub";

    /** Sent as note_url of tokens; GitHub only shows it next to the token. */
    public final static String REDIRECT_URL = OPENHUB_HOME;

    public final static List<String> COMMON_PAGE_URL_LIST = List.of(
            "https://github.com/trending"
    );

    public final static boolean isCommonPageUrl(String url){
        if(StringUtils.isBlank(url)){
            return false;
        }
        for(String commonUrl : COMMON_PAGE_URL_LIST){
            if(url.contains(commonUrl)){
                return true;
            }
        }
        return false;
    }

}
