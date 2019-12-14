import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import android.media.AudioRecord;

import io.spokestack.spokestack.android.MicrophoneInput;

public class MicrophoneInputTest {
    @Test
    public void testRead() {
        final AudioRecord recorder = mock(AudioRecord.class);
        final MicrophoneInput input = new MicrophoneInput(recorder);
        final ByteBuffer buffer = ByteBuffer.allocateDirect(42);

        verify(recorder).startRecording();

        // invalid read
        when(recorder.read(any(ByteBuffer.class), anyInt()))
            .thenReturn(1);
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() { input.read(buffer); }
        });

        // valid read
        when(recorder.read(any(ByteBuffer.class), anyInt()))
            .thenReturn(buffer.capacity());
        input.read(buffer);

        input.close();
        verify(recorder).release();
    }
}
