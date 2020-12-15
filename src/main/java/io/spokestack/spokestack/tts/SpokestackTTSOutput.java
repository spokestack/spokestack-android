package io.spokestack.spokestack.tts;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;
import io.spokestack.spokestack.util.TaskHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Audio player component for the TTS subsystem.
 *
 * <p>
 * The Spokestack audio player uses
 * <a href="https://exoplayer.dev/">ExoPlayer</a>
 * to handle automatic playback of TTS responses. Note that it
 * does not provide a UI. It is designed to be used within a TTS subsystem
 * controlled by a {@link TTSManager} in an app that wants to delegate all
 * media management to Spokestack; if fine control over
 * playback is desired, consider adding a {@link TTSListener} to the {@code
 * TTSManager} and managing audio via its methods.
 * </p>
 *
 * <p>
 * Additionally, this component requires an Android {@code Context} to be
 * attached to the manager that has created it. If the manager is meant to
 * persist across different {@code Activity}s, the {@code Context} used must
 * either be the <em>application</em> context, or it must be re-set on the
 * manager when the Activity context changes.
 * </p>
 */
public class SpokestackTTSOutput extends SpeechOutput
      implements Player.EventListener,
      AudioManager.OnAudioFocusChangeListener {

    private final int contentType;
    private final int usage;
    private TaskHandler taskHandler;
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
    @SuppressWarnings("unused")
    public SpokestackTTSOutput(SpeechConfig config) {
        // this constant's value (1) is the same in ExoPlayer as in
        // AudioAttributesCompat in versions 2.11.0 and v1.1.0, respectively,
        // which is why it's used as a stand-in for both in this class
        this.contentType = C.CONTENT_TYPE_SPEECH;

        this.playerState = new PlayerState();

        // 26 == Android O; we just don't want to pull in the extra
        // dependency to get the newer enum...we have enough imports already
        if (Build.VERSION.SDK_INT >= 26) {
            this.usage = C.USAGE_ASSISTANT;
        } else {
            this.usage = C.USAGE_MEDIA;
        }
        this.taskHandler = new TaskHandler(true);
        this.playerFactory = new PlayerFactory();
    }

    /**
     * Creates a new instance of the audio player with a custom player factory
     * and task handler. Used for testing.
     *
     * @param config  A configuration object.
     * @param factory A media player factory.
     */
    SpokestackTTSOutput(SpeechConfig config,
                        PlayerFactory factory) {
        this(config);
        this.playerFactory = factory;
        this.taskHandler = new TaskHandler(false);
    }

    @Override
    public void setAndroidContext(@NonNull Context androidContext) {
        this.appContext = androidContext;
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
        this.taskHandler.run(this::inlinePrepare);
    }

    void inlinePrepare() {
        if (this.mediaPlayer == null) {
            ExoPlayer player = this.playerFactory.createPlayer(
                  this.usage,
                  this.contentType,
                  this.appContext);
            player.addListener(this);
            this.mediaPlayer = player;
        }
    }

    @Override
    public void close() {
        this.taskHandler.run(() -> {
            if (this.mediaPlayer != null) {
                savePlayerSettings();
                this.mediaPlayer.release();
                this.mediaPlayer = null;
            }
        });
    }

    @Override
    public void audioReceived(AudioResponse response) {
        this.taskHandler.run(() -> {
            inlinePrepare();

            Uri audioUri = response.getAudioUri();

            MediaItem item = createMediaItem(audioUri);
            this.mediaPlayer.addMediaItem(item);
            this.mediaPlayer.prepare();
            this.playerState = new PlayerState(
                  true,
                  this.playerState.shouldPlay,
                  this.playerState.curPosition,
                  this.playerState.window
            );

            inlinePlay();
        });

    }

    @Override
    public void stopPlayback() {
        this.taskHandler.run(() -> {
            if (this.mediaPlayer != null) {
                mediaPlayer.stop();
            }
        });
        resetPlayerState();
    }

    @NotNull
    MediaItem createMediaItem(Uri audioUri) {
        // separated into its own method for testability
        return MediaItem.fromUri(audioUri);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        // only dispatch events if we know about the content; the player can
        // also receive state change events for audio from another source
        // (i.e., the platform ASR activation/deactivation beeps)
        if (!this.playerState.hasContent) {
            return;
        }
        if (isPlaying) {
            dispatch(new TTSEvent(TTSEvent.Type.PLAYBACK_STARTED));
        } else {
            // if playback has stopped due to an error, we rely on
            // onPlayerError to dispatch that event separately
            switch (mediaPlayer.getPlaybackState()) {
                case Player.STATE_ENDED:
                    // if the player only had one track, we'll just get
                    // onIsPlayingChanged once
                    dispatch(new TTSEvent(TTSEvent.Type.PLAYBACK_STOPPED));
                    dispatch(new TTSEvent(TTSEvent.Type.PLAYBACK_COMPLETE));
                    resetPlayerState();
                    break;
                case Player.STATE_BUFFERING:
                    // do nothing
                    break;
                default:
                    dispatch(new TTSEvent(TTSEvent.Type.PLAYBACK_STOPPED));
                    break;
            }
        }
    }

    private void resetPlayerState() {
        this.mediaPlayer.release();
        this.mediaPlayer = null;
        this.playerState = new PlayerState(false, false, 0, 0);
    }

    @Override
    public void onPlayerError(@NotNull ExoPlaybackException error) {
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

    /**
     * Start or resume playback of any TTS responses.
     */
    public void playContent() {
        this.taskHandler.run(this::inlinePlay);
    }

    void inlinePlay() {
        if (!playerState.hasContent) {
            return;
        }

        inlinePrepare();

        // only play if focus is granted
        if (requestFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mediaPlayer.play();
        }
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
        this.taskHandler.run(() -> {
            if (mediaPlayer == null) {
                return;
            }
            mediaPlayer.pause();
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
