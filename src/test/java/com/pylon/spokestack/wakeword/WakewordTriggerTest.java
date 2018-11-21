package com.pylon.spokestack.wakeword;

import java.util.*;
import java.nio.ByteBuffer;

import org.tensorflow.lite.Interpreter;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import com.pylon.spokestack.OnSpeechEventListener;
import com.pylon.spokestack.SpeechContext;
import com.pylon.spokestack.tensorflow.TensorflowModel;

public class WakewordTriggerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testTrigger() throws Exception {
        TensorflowModel.Loader loader = spy(TensorflowModel.Loader.class);
        TensorflowModel model = mock(TensorflowModel.class);
        doReturn(model).when(loader).load();

    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }
}
