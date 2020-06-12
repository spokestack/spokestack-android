package io.spokestack.spokestack.android;

import android.media.AudioRecord;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AudioRecord.class, PreASRMicrophoneInput.class})
public class PreASRMicrophoneInputTest {

    @Before
    public void before() throws Exception{
        mockStatic(AudioRecord.class);
        AudioRecord record = mock(AudioRecord.class);
        whenNew(AudioRecord.class).withAnyArguments().thenReturn(record);
        // reads are valid by default
        when(record.read(any(ByteBuffer.class), anyInt()))
              .thenAnswer((invocation) -> {
                  Object[] args = invocation.getArguments();
                  return args[1];
              });
    }

    @Test
    public void testRead() {
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 16000);
        final PreASRMicrophoneInput input =
              new PreASRMicrophoneInput(config);
        final ByteBuffer buffer = ByteBuffer.allocateDirect(42);
        final SpeechContext context = new SpeechContext(new SpeechConfig());

        // establish the AudioRecord
        input.read(context, buffer);
        AudioRecord recorder = input.getRecorder();

        // invalid read
        when(recorder.read(any(ByteBuffer.class), anyInt())).thenReturn(1);
        assertThrows(IllegalStateException.class, () ->
              input.read(context, buffer));

        verify(recorder).startRecording();

        // valid read
        when(recorder.read(any(ByteBuffer.class), anyInt()))
              .thenReturn(buffer.capacity());
        input.read(context, buffer);
        assertNotNull(input.getRecorder());

        // ASR active
        context.setActive(true);
        input.read(context, buffer);
        verify(recorder).release();
        assertNull(input.getRecorder());

        // ASR inactive - new AudioRecord is created
        context.setActive(false);
        input.read(context, buffer);
        recorder = input.getRecorder();
        assertNotNull(recorder);

        input.close();
        verify(recorder, times(2)).release();
    }

}