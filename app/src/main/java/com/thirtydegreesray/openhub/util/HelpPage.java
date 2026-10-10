package com.thirtydegreesray.openhub.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The in-app help page: assets/help.md rendered in the viewer.
 */
public final class HelpPage {

    private HelpPage() {
    }

    public static void show(@NonNull Context context) {
        StringBuilder text = new StringBuilder();
        try (InputStream in = context.getAssets().open("help.md");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } catch (IOException e) {
            return;
        }
        ViewerActivity.showMdSource(context, context.getString(R.string.help),
                MarkdownHtml.render(text.toString()));
    }
}
