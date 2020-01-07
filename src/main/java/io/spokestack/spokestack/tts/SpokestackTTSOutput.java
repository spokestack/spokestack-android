package io.spokestack.spokestack.tts;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Audio player component for the TTS subsystem.
 *
 * <p>
 * The Spokestack audio player uses
 * <a href="https://exoplayer.dev/">ExoPlayer</a>
 * to handle automatic playback of TTS responses. It responds to events in the
 * Android component lifecycle, pausing and resuming itself along with its host
 * app's activities.
 * </p>
 *
 * <p>
 * Note that this audio player does not provide a UI. It is designed to be used
 * within a TTS subsystem controlled by a {@link TTSManager} in an app that
 * wants to delegate all media management to Spokestack; if fine control over
 * playback is desired, consider adding a {@link TTSListener} to the {@code
 * TTSManager} and managing audio via its methods.
 * </p>
 */
public class SpokestackTTSOutput extends SpeechOutput
      implements Player.EventListener,
      AudioManager.OnAudioFocusChangeListener, DefaultLifecycleObserver {

    private final int contentType;
    private final int usage;
    private Consumer<Runnable> taskHandler;
    private Handler playerHandler;
    private PlayerFactory playerFactory;
    private ExoPlayer mediaPlayer;
    private Context appContext;
    private PlayerState playerState;

    /**
     * Creates a new audio output component.
     *
     * @param config A configuration object. This class does not require any
     *               configuration properties, but this constructor is required
     *               for participation in the TTS subsystem.
     */
    public SpokestackTTSOutput(@Nullable SpeechConfig config) {
        // the constant value here is the same in ExoPlayer as in
        // AudioAttributesCompat in versions 2.11.0 and v1.1.0, respectively
        this.contentType = C.CONTENT_TYPE_SPEECH;

        this.playerState = new PlayerState();

        // 26 == Android O; we just don't want to pull in the extra
        // dependency to get the newer enum...we have enough imports already
        if (Build.VERSION.SDK_INT >= 26) {
            this.usage = C.USAGE_ASSISTANT;
        } else {
            this.usage = C.USAGE_MEDIA;
        }
        this.taskHandler = this::runOnPlayerThread;
        this.playerFactory = new PlayerFactory();
    }

    private void runOnPlayerThread(Runnable task) {
        if (playerHandler == null) {
            playerHandler = new Handler(Looper.getMainLooper());
        }
        playerHandler.post(task);
    }

    /**
     * Creates a new instance of the audio player with a custom player factory
     * and task handler. Used for testing.
     *
     * @param config  A configuration object.
     * @param factory A media player factory.
     * @param handler A task handler used to interact with the media player.
     */
    SpokestackTTSOutput(SpeechConfig config,
                        PlayerFactory factory,
                        Consumer<Runnable> handler) {
        this(config);
        this.playerFactory = factory;
        this.taskHandler = handler;
    }

    @Override
    public void setAppContext(@NonNull Context context) {
        this.appContext = context;
    }

    /**
     * Gets the current media player instance.
     *
     * @return The media player being used to play TTS audio.
     */
    public @Nullable
    ExoPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    /**
     * Gets the current player state. Used for testing.
     *
     * @return The current player state.
     */
    PlayerState getPlayerState() {
        return playerState;
    }

    /**
     * Establish the media player and allocate its internal resources.
     */
    public void prepare() {
        this.taskHandler.accept(() -> {
            ExoPlayer player = this.playerFactory.createPlayer(
                  this.usage,
                  this.contentType,
                  this.appContext);
            player.addListener(this);
            this.mediaPlayer = player;
        });
    }

    @Override
    public void close() {
        this.taskHandler.accept(() -> {
            savePlayerSettings();
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        });
    }

    @Override
    public void audioReceived(AudioResponse response) {
        if (this.mediaPlayer == null) {
            prepare();
        }

        this.taskHandler.accept(() -> {
            Uri audioUri = response.getAudioUri();
            MediaSource mediaSource = createMediaSource(audioUri);
            mediaPlayer.prepare(mediaSource);
            this.playerState = new PlayerState(
                  true,
                  this.playerState.shouldPlay,
                  this.playerState.curPosition,
                  this.playerState.window
            );
        });

        playContent();
    }

    @NotNull
    MediaSource createMediaSource(Uri audioUri) {
        String userAgent = Util.getUserAgent(this.appContext, "spokestack");
        DataSource.Factory dataSourceFactory =
              new DefaultHttpDataSourceFactory(userAgent);
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
              .createMediaSource(audioUri);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            this.playerState = new PlayerState(
                  false,
                  this.playerState.shouldPlay,
                  0,
                  this.playerState.window
            );
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        TTSEvent event = new TTSEvent(TTSEvent.Type.ERROR);
        event.setError(error);
        dispatch(event);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                pauseContent();
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                playContent();
                break;
            default:
                break;
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        playContent();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        pauseContent();
    }

    /**
     * Start or resume playback of any TTS responses.
     */
    public void playContent() {
        this.taskHandler.accept(() -> {
            if (!playerState.hasContent) {
                return;
            }

            if (mediaPlayer == null) {
                prepare();
            }

            // restore player state
            mediaPlayer.seekTo(playerState.window,
                  playerState.curPosition);

            // only play if focus is granted
            if (requestFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.setPlayWhenReady(true);
            }
        });
    }

    int requestFocus() {
        // allow other audio to duck in volume since we're presumably playing
        // a short voice clip
        int focusGain = AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;

        AudioAttributesCompat attributes = new AudioAttributesCompat.Builder()
              .setUsage(usage)
              .setContentType(contentType)
              .build();

        AudioFocusRequestCompat focusRequest =
              new AudioFocusRequestCompat.Builder(focusGain)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(this)
                    .setWillPauseWhenDucked(true)
                    .build();

        AudioManager manager = (AudioManager) this.appContext
              .getSystemService(Context.AUDIO_SERVICE);

        return AudioManagerCompat.requestAudioFocus(manager, focusRequest);
    }

    /**
     * Pause playback of any current content, storing the player's state
     * internally for later resumption.
     */
    public void pauseContent() {
        this.taskHandler.accept(() -> {
            if (mediaPlayer == null) {
                return;
            }
            mediaPlayer.setPlayWhenReady(false);
            savePlayerSettings();
        });
    }

    private void savePlayerSettings() {
        this.playerState = new PlayerState(
              this.playerState.hasContent,
              mediaPlayer.getPlayWhenReady(),
              mediaPlayer.getCurrentPosition(),
              mediaPlayer.getCurrentWindowIndex());
    }

    /**
     * Internal class used to save player state when the player is being
     * destroyed/recreated.
     */
    static final class PlayerState {
        final boolean shouldPlay;
        final boolean hasContent;
        final int window;
        final long curPosition;

        PlayerState() {
            this.hasContent = false;
            this.shouldPlay = false;
            this.curPosition = 0;
            this.window = 0;
        }

        PlayerState(boolean contentReady, boolean playWhenReady,
                    long playbackPosition, int windowIndex) {
            this.hasContent = contentReady;
            this.shouldPlay = playWhenReady;
            this.curPosition = playbackPosition;
            this.window = windowIndex;
        }
    }

    /**
     * Simple class for producing media players configured with Spokestack's
     * preferred audio attributes and current context.
     */
    static class PlayerFactory {
        ExoPlayer createPlayer(int usage, int contentType,
                               Context context) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                  .setUsage(usage)
                  .setContentType(contentType)
                  .build();

            SimpleExoPlayer player =
                  new SimpleExoPlayer.Builder(context).build();
            player.setAudioAttributes(attributes, false);
            return player;
        }
    }
}
