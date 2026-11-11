package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * Plays a video attached to an issue, a comment or a post (GitHub's
 * {@code github.com/user-attachments/assets/<uuid>} link) inside the app, with Android's
 * own player. A file uploaded in this run of the app is played from the phone: GitHub
 * keeps it private until the comment is sent. Afterwards GitHub's link leads to the file.
 * When it cannot be played, the link can be opened in the browser.
 */
public class VideoPlayerActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String STATE_POSITION = "position";

    private VideoView video;
    private ProgressBar loader;
    private String url;
    private int startAt;

    public static void show(@NonNull Context context, @NonNull String url) {
        Intent intent = new Intent(context, VideoPlayerActivity.class).putExtra(EXTRA_URL, url.trim());
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        url = getIntent().getStringExtra(EXTRA_URL);
        if (StringUtils.isBlank(url)) {
            finish();
            return;
        }
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startAt = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_POSITION, 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        loader = new ProgressBar(this);
        root.addView(loader, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnPreparedListener(player -> {
            loader.setVisibility(android.view.View.GONE);
            if (startAt > 0) video.seekTo(startAt);
            video.start();
            controls.show(3000);
        });
        video.setOnErrorListener((player, what, extra) -> {
            loader.setVisibility(android.view.View.GONE);
            showCannotPlay();
            return true;
        });
        video.setOnCompletionListener(player -> controls.show(0));

        String local = Attachments.localCopy(url);
        video.setVideoURI(Uri.parse(local != null ? local : url));
    }

    private void showCannotPlay() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.video_cannot_play_title)
                .setMessage(R.string.video_cannot_play)
                .setPositiveButton(R.string.open_in_browser, (d, w) -> {
                    AppOpener.openInBrowser(this, url);
                    finish();
                })
                .setNegativeButton(R.string.close, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (video != null) outState.putInt(STATE_POSITION, video.getCurrentPosition());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) {
            startAt = video.getCurrentPosition();
            video.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
