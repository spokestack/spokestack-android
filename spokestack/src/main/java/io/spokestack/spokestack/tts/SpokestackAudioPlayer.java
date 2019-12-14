package io.spokestack.spokestack.tts;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;

/**
 * Audio player component for the TTS subsystem.
 */
public class SpokestackAudioPlayer {
    private MediaPlayer mediaPlayer;
    private AudioAttributes attributes;

    public SpokestackAudioPlayer() {
        int usage;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            usage = AudioAttributes.USAGE_ASSISTANT;
        } else {
            usage = AudioAttributes.USAGE_MEDIA;
        }

        attributes = new AudioAttributes.Builder()
              .setUsage(usage)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build();
    }

    public void onError(String message) {
        System.err.println(message);
    }

//    public void onUrlReceived(String url) {
//        val player = configMediaPlayer().apply {
//            setDataSource(url)
//            prepareAsync()
//        }
//        if (mediaPlayer == null) {
//            mediaPlayer = player
//        } else {
//            mediaPlayer ?.setNextMediaPlayer(player)
//        }
//    }
//
//    private MediaPlayer configMediaPlayer() {
//        MediaPlayer player = new MediaPlayer();
//        player.setOnPreparedListener(VoiceOutput);
//        player.setOnCompletionListener(VoiceOutput);
//        player.setAudioAttributes(attributes);
//        return player;
//    }
//
//    public void onPrepared(mp:MediaPlayer?) {
//        if (mp != null && mp == mediaPlayer) {
//            mp.start()
//        }
//    }
//
//    public void onCompletion(mp:MediaPlayer?) {
//        mp ?.release()
//        mediaPlayer = null
//    }

}
