package com.thirtydegreesray.openhub.http.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
