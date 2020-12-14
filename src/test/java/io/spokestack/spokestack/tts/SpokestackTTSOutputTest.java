package io.spokestack.spokestack.tts;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ExoPlaybackException.class)
public class SpokestackTTSOutputTest {

    @Mock
    private Context mockContext;

    @Mock
    private AudioManager mockManager;

    private final MockPlayerFactory factory = new MockPlayerFactory();

    @Before
    public void before() throws Exception {
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageName()).thenReturn("SpokestackOutputTest");
        PackageManager packageManager = mock(PackageManager.class);
        PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.versionName = "1e-3";
        when(packageManager.getPackageInfo(anyString(), anyInt()))
              .thenReturn(packageInfo);
        when(mockContext.getPackageManager()).thenReturn(packageManager);
        // allow all requests for audio focus
        when(mockManager.requestAudioFocus(any(), anyInt(), anyInt()))
              .thenReturn(AudioManager.AUDIOFOCUS_GAIN);
        when(mockContext.getSystemService(Context.AUDIO_SERVICE))
              .thenReturn(mockManager);
    }

    @Test
    public void testConstruction() {
        SpokestackTTSOutput ttsOutput =
              new SpokestackTTSOutput(null, factory);
        assertNull(ttsOutput.getMediaPlayer());

        // no errors thrown
        ttsOutput.pauseContent();
        ttsOutput.playContent();
    }

    @Test
    public void testResourceManagement() {
        factory.needMock = true;
        SpokestackTTSOutput ttsOutput =
              new SpokestackTTSOutput(null, factory);
        ttsOutput.setAndroidContext(mockContext);
        ttsOutput.prepare();

        ExoPlayer mediaPlayer = ttsOutput.getMediaPlayer();
        assertNotNull(mediaPlayer);
        // no content, so nothing to play
        assertFalse(mediaPlayer.getPlayWhenReady());

        ttsOutput.close();
        mediaPlayer = ttsOutput.getMediaPlayer();
        assertNull(mediaPlayer);
    }

    @Test
    public void testCallbacks() {
        factory.needMock = true;
        SpokestackTTSOutput ttsOutput = spiedOutput();

        ExoPlayer mediaPlayer = ttsOutput.getMediaPlayer();
        assertNull(mediaPlayer);

        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));

        // audioReceived ensures that the player gets set up
        mediaPlayer = ttsOutput.getMediaPlayer();
        assertNotNull(mediaPlayer);
        verify(ttsOutput, times(1)).inlinePlay();
        verify(ttsOutput, times(1)).createMediaSource(Uri.EMPTY);
        verify(ttsOutput, times(1)).requestFocus();
        verify(mediaPlayer, times(1)).prepare(any());
        verify(mediaPlayer, times(1)).setPlayWhenReady(true);
    }

    @Test
    public void testPlayerStateChange() {
        SpokestackTTSOutput ttsOutput = spiedOutput();
        Listener listener = new Listener();
        ttsOutput.addListener(listener);
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        assertTrue(ttsOutput.getPlayerState().hasContent);

        TestExoPlayer mediaPlayer = (TestExoPlayer) ttsOutput.getMediaPlayer();
        assertNotNull(mediaPlayer);
        // buffering doesn't send PLAYBACK_STOPPED events
        mediaPlayer.setPlaybackState(Player.STATE_BUFFERING);
        ttsOutput.onIsPlayingChanged(false);
        assertTrue(ttsOutput.getPlayerState().hasContent);
        assertTrue(listener.events.isEmpty());

        // simulate playing audio from a non-Spokestack source
        ttsOutput.stopPlayback();
        ttsOutput.onIsPlayingChanged(true);
        ttsOutput.onIsPlayingChanged(false);
        assertFalse(ttsOutput.getPlayerState().shouldPlay);
        assertFalse(ttsOutput.getPlayerState().hasContent);
        // no event for non-Spokestack audio
        assertTrue(listener.events.isEmpty());

        // give it Spokestack audio to play
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        // the player has to be recreated after `stopPlayback()` and receiving
        // new audio
        mediaPlayer = (TestExoPlayer) ttsOutput.getMediaPlayer();
        ttsOutput.onIsPlayingChanged(true);
        ttsOutput.onIsPlayingChanged(false);
        assertEquals(2, listener.events.size());
        assertEquals(TTSEvent.Type.PLAYBACK_STARTED,
              listener.events.get(0).type);
        assertEquals(TTSEvent.Type.PLAYBACK_STOPPED,
              listener.events.get(1).type);

        mediaPlayer.setPlaybackState(Player.STATE_ENDED);
        ttsOutput.onIsPlayingChanged(false);
        assertEquals(4, listener.events.size());
        assertEquals(TTSEvent.Type.PLAYBACK_STOPPED,
              listener.events.get(2).type);
        assertEquals(TTSEvent.Type.PLAYBACK_COMPLETE,
              listener.events.get(3).type);

        mediaPlayer.setPlaybackState(Player.STATE_BUFFERING);
        ttsOutput.onIsPlayingChanged(false);
        assertEquals(4, listener.events.size());

        // remove the listener and make sure it doesn't receive a fourth event
        ttsOutput.removeListener(listener);
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        ttsOutput.onIsPlayingChanged(true);
        assertEquals(4, listener.events.size());
    }

    @Test
    public void testPlayerError() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        SpokestackTTSOutput ttsOutput = spiedOutput();
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        ttsOutput.addListener(event -> {
            if (event.getError() == null) {
                fail("Error should be received by listener");
            }
            listenerCalled.set(true);
        });
        ttsOutput.onPlayerError(PowerMockito.mock(ExoPlaybackException.class));
        assertTrue(listenerCalled.get());
    }

    @Test
    public void testFocusChanged() {
        factory.needMock = true;
        SpokestackTTSOutput ttsOutput = spiedOutput();
        assertFalse(ttsOutput.getPlayerState().shouldPlay);
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        ExoPlayer mediaPlayer = ttsOutput.getMediaPlayer();
        ttsOutput.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        verify(mediaPlayer, times(1)).setPlayWhenReady(false);
        ttsOutput.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        // once for the audioReceived call, once for the focus gain
        verify(mediaPlayer, times(2)).setPlayWhenReady(true);

        // simulate a denied focus request
        doReturn(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
              .when(ttsOutput).requestFocus();
        ttsOutput.audioReceived(new AudioResponse(Uri.EMPTY));
        // media player shouldn't be told to play this time
        verify(mediaPlayer, times(2)).setPlayWhenReady(true);
    }

    @Test
    public void testCompatibility() {
        SpokestackTTSOutput ttsOutput =
              new SpokestackTTSOutput(null, factory);

        // these methods are implemented solely to maintain compatibility with
        // older Android APIs; calling them should do nothing
        Timeline timeline = new SinglePeriodTimeline(0, false, false, false);
        ttsOutput.onTimelineChanged(timeline, 0);
        ttsOutput.onTimelineChanged(timeline, null, 0);
        TrackGroupArray groupArray = new TrackGroupArray();
        TrackSelectionArray selectionArray = new TrackSelectionArray();
        ttsOutput.onTracksChanged(groupArray, selectionArray);
        ttsOutput.onLoadingChanged(true);
        ttsOutput.onPlaybackSuppressionReasonChanged(0);
        ttsOutput.onIsPlayingChanged(false);
        ttsOutput.onRepeatModeChanged(0);
        ttsOutput.onShuffleModeEnabledChanged(false);
        ttsOutput.onPositionDiscontinuity(-10);
        PlaybackParameters params = new PlaybackParameters(1.0f);
        ttsOutput.onPlaybackParametersChanged(params);
        ttsOutput.onSeekProcessed();
    }

    private SpokestackTTSOutput spiedOutput() {
        SpokestackTTSOutput ttsOutput =
              spy(new SpokestackTTSOutput(null, factory));
        // mocked because Android system methods called indirectly by the code
        // under test are all stubbed or absent from the android/androidx deps
        doReturn(mock(MediaSource.class))
              .when(ttsOutput).createMediaSource(any());
        doReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
              .when(ttsOutput).requestFocus();
        return ttsOutput;
    }

    private static class MockPlayerFactory
          extends SpokestackTTSOutput.PlayerFactory {

        public boolean needMock;

        @Override
        TestExoPlayer createPlayer(int usage, int contentType,
                               Context context) {
            if (needMock) {
                return mock(TestExoPlayer.class);
            }
            return new TestExoPlayer();
        }
    }

    private static class Listener implements TTSListener {
        List<TTSEvent> events = new ArrayList<>();

        @Override
        public void eventReceived(@NonNull TTSEvent event) {
            this.events.add(event);
        }
    }
}
