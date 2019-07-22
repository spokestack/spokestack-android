package com.pylon.spokestack.tensorflow;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.tensorflow.lite.Interpreter;

/**
 * Tensorflow-Lite model wrapper and loader
 *
 * <p>
 * This class wraps the TF-Lite interpreter, so that it can be mocked for
 * unit testing on non-android platforms. It also encapsulates the input/output
 * byte buffers used for passing input tensors into a model and retrieving
 * outputs.
 * </p>
 */
public class TensorflowModel implements AutoCloseable {
    private final Interpreter interpreter;
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private ByteBuffer stateInputBuffer;
    private ByteBuffer stateOutputBuffer;

    private final Object[] inputArray;
    private final Map<Integer, Object> outputMap;

    /**
     * constructs a new tensorflow model.
     * @param loader the loader (builder) for the model
     */
    public TensorflowModel(Loader loader) {
        this.interpreter = new Interpreter(new File(loader.path));
        this.inputBuffer = loader.inputShape != 0
            ? ByteBuffer
                .allocateDirect(loader.inputShape * loader.inputSize)
                .order(ByteOrder.nativeOrder())
            : null;
        this.outputBuffer = loader.outputShape != 0
            ? ByteBuffer
                .allocateDirect(loader.outputShape * loader.outputSize)
                .order(ByteOrder.nativeOrder())
            : null;
        this.stateInputBuffer = loader.stateShape != 0
            ? ByteBuffer
                .allocateDirect(loader.stateShape * loader.stateSize)
                .order(ByteOrder.nativeOrder())
            : null;
        this.stateOutputBuffer = loader.stateShape != 0
            ? ByteBuffer
                .allocateDirect(loader.stateShape * loader.stateSize)
                .order(ByteOrder.nativeOrder())
            : null;

        this.inputArray = new Object[this.stateInputBuffer != null ? 2 : 1];
        this.outputMap = new HashMap<>();
    }

    /**
     * releases the tensorflow interpreter.
     */
    public void close() {
        this.interpreter.close();
    }

    /**
     * @return the input tensor buffer
     */
    public ByteBuffer inputs() {
        return this.inputBuffer;
    }

    /**
     * @return the state tensor buffer
     */
    public ByteBuffer states() {
        return this.stateInputBuffer;
    }

    /**
     * @return the output tensor buffer
     */
    public ByteBuffer outputs() {
        return this.outputBuffer;
    }

    /**
     * executes the model using the attached buffers.
     */
    public void run() {
        this.inputBuffer.rewind();
        this.outputBuffer.rewind();
        if (this.stateInputBuffer != null) {
            this.stateInputBuffer.rewind();
            this.stateOutputBuffer.rewind();
        }

        this.inputArray[0] = this.inputBuffer;
        this.outputMap.put(0, this.outputBuffer);
        if (this.stateInputBuffer != null) {
            this.inputArray[1] = this.stateInputBuffer;
            this.outputMap.put(1, this.stateOutputBuffer);
        }

        this.interpreter.runForMultipleInputsOutputs(
            this.inputArray,
            this.outputMap);

        this.inputBuffer.rewind();
        this.outputBuffer.rewind();
        if (this.stateInputBuffer != null) {
            ByteBuffer temp = this.stateInputBuffer;
            this.stateInputBuffer = this.stateOutputBuffer;
            this.stateOutputBuffer = temp;
            this.stateInputBuffer.rewind();
            this.stateOutputBuffer.rewind();
        }
    }

    /**
     * loader (builder) class for the tensorflow model.
     */
    public static class Loader {
        /** tensor data types. */
        public enum DType {
            /** 32-bit floating tensor. */
            FLOAT
        }

        private String path;
        private int inputShape;
        private int inputSize;
        private int outputShape;
        private int outputSize;
        private int stateShape;
        private int stateSize;

        /**
         * initializes a new loader instance.
         */
        public Loader() {
            this.reset();
        }

        /**
         * resets the loader to the default state.
         * @return this
         */
        public Loader reset() {
            this.path = null;
            this.inputShape = 0;
            this.inputSize = 4;
            this.outputShape = 0;
            this.outputSize = 4;
            this.stateShape = 0;
            this.stateSize = 4;
            return this;
        }

        /**
         * sets the file system path to the TF-Lite model.
         * @param value value to assign
         * @return this
         */
        public Loader setPath(String value) {
            this.path = value;
            return this;
        }

        /**
         * sets the shape (product of shape dimensions) of the input tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setInputShape(int value) {
            this.inputShape = value;
            return this;
        }

        /**
         * sets the data type of the input tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setInputType(DType value) {
            if (value == DType.FLOAT)
                this.inputSize = 4;
            else
                throw new IllegalArgumentException("value");
            return this;
        }

        /**
         * sets the shape (product of shape dimensions) of the state tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setStateShape(int value) {
            this.stateShape = value;
            return this;
        }

        /**
         * sets the data type of the state tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setStateType(DType value) {
            if (value == DType.FLOAT)
                this.stateSize = 4;
            else
                throw new IllegalArgumentException("value");
            return this;
        }

        /**
         * sets the shape (product of shape dimensions) of the output tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setOutputShape(int value) {
            this.outputShape = value;
            return this;
        }

        /**
         * sets the data type of the output tensor.
         * @param value value to assign
         * @return this
         */
        public Loader setOutputType(DType value) {
            if (value == DType.FLOAT)
                this.outputSize = 4;
            else
                throw new IllegalArgumentException("value");
            return this;
        }

        /**
         * loads the tensorflow model using the attached configuration.
         * @return the new tensorflow model
         */
        public TensorflowModel load() {
            TensorflowModel model = new TensorflowModel(this);
            this.reset();
            return model;
        }
    }
}
