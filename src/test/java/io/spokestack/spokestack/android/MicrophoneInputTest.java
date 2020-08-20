import java.nio.ByteBuffer;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.android.AudioRecordError;
import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import android.media.AudioRecord;

import io.spokestack.spokestack.android.MicrophoneInput;

public class MicrophoneInputTest {
    @Test
    public void testRead() throws AudioRecordError {
        final AudioRecord recorder = mock(AudioRecord.class);
        final MicrophoneInput input = new MicrophoneInput(recorder);
        final ByteBuffer buffer = ByteBuffer.allocateDirect(42);
        final SpeechContext context = new SpeechContext(new SpeechConfig());

        verify(recorder).startRecording();

        // invalid read
        when(recorder.read(any(ByteBuffer.class), anyInt()))
            .thenReturn(1);
        assertThrows(AudioRecordError.class, () -> input.read(context, buffer));

        // valid read
        when(recorder.read(any(ByteBuffer.class), anyInt()))
            .thenReturn(buffer.capacity());
        input.read(context, buffer);

        input.close();
        verify(recorder).release();
    }
}
