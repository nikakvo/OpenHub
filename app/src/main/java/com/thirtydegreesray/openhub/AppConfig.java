

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

    /**
     * The REST API version every request to api.github.com asks for (header
     * X-GitHub-Api-Version). Without it GitHub answers with 2022-11-28.
     */
    public final static String GITHUB_API_VERSION = "2026-03-10";
    public final static String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";

    public final static String GITHUB_CONTENT_BASE_URL = "https://raw.githubusercontent.com/";

    public final static int HTTP_TIME_OUT = 32 * 1000;

    public final static int HTTP_MAX_CACHE_SIZE = 32 * 1024 * 1024;

    public final static int IMAGE_MAX_CACHE_SIZE = 32 * 1024 * 1024;

    public final static int CACHE_MAX_AGE = 4 * 7 * 24 * 60 * 60;

    public final static String DB_NAME = "OpenHub.db";



    public final static String OPENHUB_CLIENT_ID = BuildConfig.OPENHUB_CLIENT_ID;

    public final static String OPENHUB_CLIENT_SECRET = BuildConfig.OPENHUB_CLIENT_SECRET;

    /**
     * workflow: create or change files under .github/workflows;
     * delete_repo: delete a repository from its settings
     */
    public final static String OAUTH2_SCOPE = "user,repo,workflow,delete_repo,gist,notifications";

    /**
     * Added to the browser sign-in only: GitHub then gives an expiring token (8 hours) and a
     * refresh token (about 6 months) for this sign-in, without the OAuth App opting in for
     * everyone. It is not a real scope: no extra question on GitHub's page.
     */
    public final static String OAUTH2_EXPIRING_SCOPE = "offline_access";


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
