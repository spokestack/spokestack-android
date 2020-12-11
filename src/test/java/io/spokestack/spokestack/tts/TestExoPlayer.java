package io.spokestack.spokestack.tts;

import android.os.Looper;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple stubbed media player for testing TTS events.
 *
 * Tests should ideally use ExoPlayerTestRunner instead, but as of this writing
 * the artifacts for exoplayer-testutils and its dependents are
 * in such a state of disarray (broken links, missing versions, etc.)
 * as to make including them infeasible.
 */
public class TestExoPlayer implements ExoPlayer {

    private int playbackState;

    public TestExoPlayer() {
    }

    @Override
    public int getPlaybackState() {
        return this.playbackState;
    }

    public void setPlaybackState(int playbackState) {
        this.playbackState = playbackState;
    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        return null;
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        return null;
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        return null;
    }

    @Nullable
    @Override
    public MetadataComponent getMetadataComponent() {
        return null;
    }

    @Override
    public Looper getApplicationLooper() {
        return null;
    }

    @Override
    public void addListener(EventListener eventListener) {
    }

    @Override
    public void removeListener(EventListener eventListener) {
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return 0;
    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Nullable
    @Override
    public ExoPlaybackException getPlaybackError() {
        return null;
    }

    @Override
    public void setPlayWhenReady(boolean b) {
    }

    @Override
    public boolean getPlayWhenReady() {
        return false;
    }

    @Override
    public void setRepeatMode(int i) {
    }

    @Override
    public int getRepeatMode() {
        return 0;
    }

    @Override
    public void setShuffleModeEnabled(boolean b) {
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void seekToDefaultPosition() {
    }

    @Override
    public void seekToDefaultPosition(int i) {
    }

    @Override
    public void seekTo(long l) {
    }

    @Override
    public void seekTo(int i, long l) {
    }

    @Override
    public boolean hasPrevious() {
        return false;
    }

    @Override
    public void previous() {
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public void next() {
    }

    @Override
    public void setPlaybackParameters(
          @Nullable PlaybackParameters playbackParameters) {

    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return null;
    }

    @Override
    public void stop() {

    }

    @Override
    public void stop(boolean b) {

    }

    @Override
    public void release() {

    }

    @Override
    public int getRendererCount() {
        return 0;
    }

    @Override
    public int getRendererType(int i) {
        return 0;
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        return null;
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        return null;
    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
        return null;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return null;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return 0;
    }

    @Override
    public int getCurrentWindowIndex() {
        return 0;
    }

    @Override
    public int getNextWindowIndex() {
        return 0;
    }

    @Override
    public int getPreviousWindowIndex() {
        return 0;
    }

    @Nullable
    @Override
    public Object getCurrentTag() {
        return null;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public long getCurrentPosition() {
        return 0;
    }

    @Override
    public long getBufferedPosition() {
        return 0;
    }

    @Override
    public int getBufferedPercentage() {
        return 0;
    }

    @Override
    public long getTotalBufferedDuration() {
        return 0;
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return false;
    }

    @Override
    public boolean isCurrentWindowLive() {
        return false;
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return false;
    }

    @Override
    public boolean isPlayingAd() {
        return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return 0;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return 0;
    }

    @Override
    public long getContentDuration() {
        return 0;
    }

    @Override
    public long getContentPosition() {
        return 0;
    }

    @Override
    public long getContentBufferedPosition() {
        return 0;
    }

    @Override
    public Looper getPlaybackLooper() {
        return null;
    }

    @Override
    public void retry() {
    }

    @Override
    public void prepare(MediaSource mediaSource) {
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean b, boolean b1) {
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        return null;
    }

    @Override
    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    }

    @NotNull
    @Override
    public SeekParameters getSeekParameters() {
        return null;
    }

    @Override
    public void setForegroundMode(boolean b) {
    }
}
