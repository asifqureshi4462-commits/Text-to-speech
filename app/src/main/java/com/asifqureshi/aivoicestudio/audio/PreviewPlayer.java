package com.asifqureshi.aivoicestudio.audio;

import android.content.Context;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/** Plays one provider-hosted voice preview at a time. */
public final class PreviewPlayer {
    public interface Callback {
        void onStateChanged(String playingVoiceId);
        void onError(String voiceId);
    }

    private final Context context;
    private final Callback callback;
    private ExoPlayer player;
    private String currentId;

    public PreviewPlayer(Context c, Callback cb) {
        context = c.getApplicationContext();
        callback = cb;
    }

    public String getPlayingId() { return currentId; }

    /** Toggles: tapping the playing voice stops it, otherwise starts the new preview. */
    public void toggle(String voiceId, String url) {
        if (voiceId.equals(currentId)) { stop(); return; }
        stop();
        player = new ExoPlayer.Builder(context)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        currentId = voiceId;
        final String id = voiceId;
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && id.equals(currentId)) stop();
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (id.equals(currentId)) { stop(); callback.onError(id); }
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        callback.onStateChanged(currentId);
    }

    public void stop() {
        if (player != null) { player.release(); player = null; }
        currentId = null;
        callback.onStateChanged(null);
    }
}
