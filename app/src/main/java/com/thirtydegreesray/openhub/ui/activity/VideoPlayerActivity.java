package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.core.MediaHttp;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.StringUtils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Plays a video attached to an issue, a comment or a post (GitHub's
 * {@code github.com/user-attachments/assets/<uuid>} link) inside the app with Media3
 * ExoPlayer: its own decoders and extractors (MP4, MOV, WebM, ...), seek bar, the video's
 * proportions in every orientation, and the reason when a video cannot be played. The
 * network goes through the app's OkHttp ({@link MediaHttp}). A file uploaded in this run
 * of the app is played from the phone: GitHub keeps it private until the comment is sent.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String STATE_POSITION = "position";

    private PlayerView view;
    @Nullable private ExoPlayer player;
    private String url;
    private long startAt;
    @Nullable private Disposable checking;
    /** the file type check is still running: a player error waits for its answer */
    private boolean typeUnknown;
    @Nullable private PlaybackException waitingError;
    /** the play / pause button of the small window (picture in picture) */
    @Nullable private android.content.BroadcastReceiver pipActions;
    private static final String ACTION_PIP_TOGGLE = "io.github.nikakvo.openhub.PIP_TOGGLE";

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
        startAt = savedInstanceState == null ? 0 : savedInstanceState.getLong(STATE_POSITION, 0);

        view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        view.setKeepScreenOn(true);
        setContentView(view);
        keepOutOfSystemBars();

        // content:// and file:// from the phone, http(s) through the app's OkHttp
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this,
                new OkHttpDataSource.Factory(MediaHttp.client()));
        ExoPlayer exo = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .build();
        player = exo;
        view.setPlayer(exo);
        exo.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (typeUnknown) {
                    waitingError = error;
                } else {
                    showCannotPlay(error);
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                // at the end the controls stay, so it can be played again
                if (state == Player.STATE_ENDED && !inPip()) view.showController();
                updatePip();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePip();
            }

            @Override
            public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize size) {
                updatePip();
            }
        });
        updatePip();

        String local = Attachments.localCopy(url);
        play(local != null ? Uri.parse(local) : Uri.parse(url));
        if (local != null) return;

        // a bare link of a picture (pasted instead of ![..](..)) is not a video: GitHub is
        // asked what the file is while the player already loads it
        typeUnknown = true;
        checking = Single.fromCallable(() -> contentType(url))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::typeKnown, error -> typeKnown(""));
    }

    /**
     * The window is drawn edge to edge (target SDK 35+): without this the seek bar, the
     * times and the settings button lie under the navigation bar and the top of the video
     * under the status bar. The bars stay black with light icons.
     */
    private void keepOutOfSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window,
                window.getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void play(@NonNull Uri uri) {
        if (player == null) return;
        player.setMediaItem(MediaItem.fromUri(uri), startAt);
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void typeKnown(@NonNull String type) {
        typeUnknown = false;
        if (type.startsWith("image/")) {
            releasePlayer();
            ViewerActivity.showImage(this, url);
            finish();
            return;
        }
        if (waitingError != null) {
            PlaybackException error = waitingError;
            waitingError = null;
            showCannotPlay(error);
        }
    }

    /** The Content-Type of the file behind a link ("" when unknown); reads one byte. */
    @NonNull
    private static String contentType(@NonNull String url) throws java.io.IOException {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url)
                .header("Range", "bytes=0-0").build();
        try (okhttp3.Response response = MediaHttp.client().newCall(request).execute()) {
            String type = response.header("Content-Type");
            return type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private void showCannotPlay(@NonNull PlaybackException error) {
        if (isFinishing()) return;
        // the reason in Media3's words, e.g. ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        String message = getString(R.string.video_cannot_play, error.getErrorCodeName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.video_cannot_play_title)
                .setMessage(message)
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
        if (player != null) outState.putLong(STATE_POSITION, player.getCurrentPosition());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // in the small window the video goes on playing
        if (player != null && !inPip()) player.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // the small window was closed (or the screen left any other way): stop playing
        if (player != null) player.pause();
        if (closedFromPip) finish();
    }

    /** set when the small window goes away without going back to full screen */
    private boolean closedFromPip;

    private boolean inPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode();
    }

    private boolean pipAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(
                        android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /**
     * The small window: as wide as the video (Android allows 1:2.39 to 2.39:1), with a play
     * / pause button. On Android 12+ it opens by itself when the user goes home while the
     * video plays; before, {@link #onUserLeaveHint()} opens it.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    private android.app.PictureInPictureParams pipParams() {
        android.app.PictureInPictureParams.Builder builder =
                new android.app.PictureInPictureParams.Builder();
        androidx.media3.common.VideoSize size = player == null
                ? androidx.media3.common.VideoSize.UNKNOWN : player.getVideoSize();
        int[] ratio = com.thirtydegreesray.openhub.util.PipRatio.of(size.width, size.height,
                size.pixelWidthHeightRatio);
        if (ratio != null) builder.setAspectRatio(new android.util.Rational(ratio[0], ratio[1]));
        boolean playing = player != null && player.isPlaying();
        Intent toggle = new Intent(ACTION_PIP_TOGGLE).setPackage(getPackageName());
        android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(this, 0, toggle,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        String label = getString(playing ? R.string.video_pause : R.string.video_play);
        android.app.RemoteAction action = new android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, playing
                        ? androidx.media3.ui.R.drawable.exo_icon_pause
                        : androidx.media3.ui.R.drawable.exo_icon_play),
                label, label, pending);
        builder.setActions(java.util.Collections.singletonList(action));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(playing);
            builder.setSeamlessResizeEnabled(false);
        }
        return builder.build();
    }

    private void updatePip() {
        if (!pipAvailable() || isFinishing() || isDestroyed()) return;
        try {
            setPictureInPictureParams(pipParams());
        } catch (RuntimeException ignored) {
            // the system refused the values (e.g. no picture in picture on this phone)
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Android 12+ opens the small window itself (auto enter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !pipAvailable()) return;
        if (player != null && player.isPlaying()) {
            try {
                enterPictureInPictureMode(pipParams());
            } catch (RuntimeException ignored) {
                // picture in picture switched off for the app in the phone settings
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip,
                                              @NonNull android.content.res.Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config);
        if (inPip) {
            closedFromPip = false;
            view.hideController();
            view.setUseController(false);
            registerPipActions();
        } else {
            view.setUseController(true);
            unregisterPipActions();
            // back to full screen: the screen is started again; closed: it only stops
            if (!getLifecycle().getCurrentState().isAtLeast(
                    androidx.lifecycle.Lifecycle.State.STARTED)) {
                closedFromPip = true;
                if (player != null) player.pause();
                finish();
            }
        }
    }

    private void registerPipActions() {
        if (pipActions != null) return;
        pipActions = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (player == null || !ACTION_PIP_TOGGLE.equals(intent.getAction())) return;
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
                    player.play();
                }
            }
        };
        androidx.core.content.ContextCompat.registerReceiver(this, pipActions,
                new android.content.IntentFilter(ACTION_PIP_TOGGLE),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterPipActions() {
        if (pipActions == null) return;
        try {
            unregisterReceiver(pipActions);
        } catch (IllegalArgumentException ignored) {
        }
        pipActions = null;
    }

    private void releasePlayer() {
        if (player != null) {
            if (view != null) view.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        unregisterPipActions();
        if (checking != null) checking.dispose();
        releasePlayer();
        super.onDestroy();
    }
}
