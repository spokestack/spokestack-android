package com.pylon.spokestack.tensorflow;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
     * @return the output tensor buffer
     */
    public ByteBuffer outputs() {
        return this.outputBuffer;
    }

    /**
     * executes the model using the attached buffers.
     */
    public void run() {
        run(this.inputs(), this.outputs());
    }

    /**
     * executes the model using specified buffers.
     * @param inputs input tensor buffer
     * @param outputs output tensor buffer
     */
    public void run(ByteBuffer inputs, ByteBuffer outputs) {
        inputs.rewind();
        outputs.rewind();

        this.interpreter.run(inputs, outputs);

        inputs.rewind();
        outputs.rewind();
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
        private int inputShape = 0;
        private int inputSize = 4;
        private int outputShape = 0;
        private int outputSize = 4;

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
            return new TensorflowModel(this);
        }
    }
}
