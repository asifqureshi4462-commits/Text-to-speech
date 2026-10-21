package com.asifqureshi.aivoicestudio.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.utils.Format;

import java.io.File;

/**
 * Reusable audio player (AndroidX Media3 ExoPlayer) with play/pause, +/-10s seek, seek bar, position/duration,
 * playback speed, audio-focus handling and "becoming noisy" (headphones unplugged) pause.
 * Call {@link #release()} when the hosting view is destroyed. Playback stops when the app leaves the foreground.
 */
public class AudioPlayerView extends LinearLayout {
    public interface Listener { void onDurationKnown(long durationMs); }

    private static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private final ImageButton btnPlay, btnBack, btnForward;
    private final SeekBar seek;
    private final TextView position, duration, speedButton, errorText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private Listener listener;
    private boolean userSeeking, durationReported;
    private int speedIndex = 1;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            if (player != null && player.isPlaying()) handler.postDelayed(this, 250);
        }
    };

    public AudioPlayerView(Context context) { this(context, null); }

    public AudioPlayerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_audio_player, this, true);
        btnPlay = findViewById(R.id.btnPlayPause);
        btnBack = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        seek = findViewById(R.id.seekBar);
        position = findViewById(R.id.textPosition);
        duration = findViewById(R.id.textDuration);
        speedButton = findViewById(R.id.btnSpeed);
        errorText = findViewById(R.id.textPlayerError);

        float saved = App.from(context).prefs.getPlaybackSpeed();
        for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == saved) speedIndex = i;
        updateSpeedLabel();

        btnPlay.setOnClickListener(v -> togglePlay());
        btnBack.setOnClickListener(v -> seekBy(-10_000));
        btnForward.setOnClickListener(v -> seekBy(10_000));
        speedButton.setOnClickListener(v -> {
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            App.from(getContext()).prefs.setPlaybackSpeed(SPEEDS[speedIndex]);
            if (player != null) player.setPlaybackSpeed(SPEEDS[speedIndex]);
            updateSpeedLabel();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) position.setText(Format.duration(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (player != null) player.seekTo(s.getProgress());
            }
        });
    }

    public void setListener(Listener l) { listener = l; }

    private void updateSpeedLabel() {
        speedButton.setText(getContext().getString(R.string.speed_format, SPEEDS[speedIndex]));
    }

    /** Loads a local audio file. Replaces any previous item. */
    public void load(File file, boolean autoPlay) {
        release();
        errorText.setVisibility(GONE);
        durationReported = false;
        player = new ExoPlayer.Builder(getContext())
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.setPlaybackSpeed(SPEEDS[speedIndex]);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) onReady();
                refresh();
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                btnPlay.setContentDescription(getContext().getString(isPlaying ? R.string.pause : R.string.play));
                handler.removeCallbacks(ticker);
                if (isPlaying) handler.post(ticker);
                refresh();
            }
            @Override public void onPlayerError(PlaybackException error) {
                errorText.setText(R.string.playback_error);
                errorText.setVisibility(VISIBLE);
            }
        });
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.setPlayWhenReady(autoPlay);
        player.prepare();
        seek.setProgress(0);
        position.setText(Format.duration(0));
    }

    private void onReady() {
        long d = player.getDuration();
        if (d == C.TIME_UNSET || d <= 0) return;
        seek.setMax((int) Math.min(d, Integer.MAX_VALUE));
        duration.setText(Format.duration(d));
        if (!durationReported) {
            durationReported = true;
            if (listener != null) listener.onDurationKnown(d);
        }
    }

    private void refresh() {
        if (player == null || userSeeking) return;
        long p = player.getCurrentPosition();
        seek.setProgress((int) Math.min(p, Integer.MAX_VALUE));
        position.setText(Format.duration(p));
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
        if (player.isPlaying()) player.pause(); else player.play();
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long target = Math.max(0, player.getCurrentPosition() + deltaMs);
        long d = player.getDuration();
        if (d != C.TIME_UNSET) target = Math.min(target, d);
        player.seekTo(target);
        refresh();
    }

    public void pause() { if (player != null) player.pause(); }

    public void release() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            player.release();
            player = null;
        }
        btnPlay.setImageResource(R.drawable.ic_play);
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }
}
