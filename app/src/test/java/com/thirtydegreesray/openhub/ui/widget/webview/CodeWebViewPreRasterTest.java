package com.thirtydegreesray.openhub.ui.widget.webview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.ViewGroup;

import org.junit.Test;

public class CodeWebViewPreRasterTest {

    @Test
    public void onlyWebViewsAsTallAsTheirPage() {
        assertTrue(CodeWebView.preRasterFor(ViewGroup.LayoutParams.WRAP_CONTENT));
        assertFalse(CodeWebView.preRasterFor(ViewGroup.LayoutParams.MATCH_PARENT));
        assertFalse(CodeWebView.preRasterFor(600));
    }
}
