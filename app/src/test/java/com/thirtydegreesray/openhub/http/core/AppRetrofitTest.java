package com.thirtydegreesray.openhub.http.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import okhttp3.HttpUrl;

public class AppRetrofitTest {

    @Test
    public void theTokenGoesOnlyToTheGitHubApi() {
        assertTrue(AppRetrofit.isTokenHost("api.github.com"));
        assertTrue(AppRetrofit.isTokenHost("API.GitHub.com"));
        assertFalse(AppRetrofit.isTokenHost("github.com"));
        assertFalse(AppRetrofit.isTokenHost("raw.githubusercontent.com"));
        assertFalse(AppRetrofit.isTokenHost("api.github.com.example.com"));
        assertFalse(AppRetrofit.isTokenHost("example.com"));
        assertFalse(AppRetrofit.isTokenHost(null));
    }

    @Test
    public void theTokenTravelsOnlyOverHttps() {
        assertTrue(AppRetrofit.sendsTokenTo(HttpUrl.get("https://api.github.com/user")));
        assertTrue(AppRetrofit.sendsTokenTo(HttpUrl.get("https://API.github.com/repos/a/b")));
        assertFalse(AppRetrofit.sendsTokenTo(HttpUrl.get("http://api.github.com/user")));
        assertFalse(AppRetrofit.sendsTokenTo(HttpUrl.get("https://github.com/a/b")));
        assertFalse(AppRetrofit.sendsTokenTo(HttpUrl.get("https://api.github.com.evil.example/user")));
        assertFalse(AppRetrofit.sendsTokenTo(HttpUrl.get("https://objects.githubusercontent.com/x")));
        assertFalse(AppRetrofit.sendsTokenTo(null));
    }
}
