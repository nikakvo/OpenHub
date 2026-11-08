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

    @Test
    public void accountTagOnlyOnCachedReads() {
        assertTrue(AppRetrofit.tagsAccount("GET", null));
        assertTrue(AppRetrofit.tagsAccount("get", "max-age=60"));
        assertFalse(AppRetrofit.tagsAccount("GET", "no-cache, no-store"));
        assertFalse(AppRetrofit.tagsAccount("GET", "No-Store"));
        assertFalse(AppRetrofit.tagsAccount("POST", null));
        assertFalse(AppRetrofit.tagsAccount("PATCH", null));
        assertFalse(AppRetrofit.tagsAccount("DELETE", null));
        assertFalse(AppRetrofit.tagsAccount("PUT", null));
    }
}
