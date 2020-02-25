package io.spokestack.spokestack.tensorflow;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tensorflow-Lite model wrapper and loader
 *
 * <p>
 * This class wraps the TensorFlow Lite interpreter so that it can be mocked
 * for unit testing on non-android platforms. It also encapsulates the
 * input/output byte buffers used for passing input tensors into a model
 * and retrieving outputs.
 * </p>
 */
public class TensorflowModel implements AutoCloseable {
    private final Interpreter interpreter;
    private final List<ByteBuffer> inputBuffers = new ArrayList<>();
    private final List<ByteBuffer> outputBuffers = new ArrayList<>();
    private final int inputSize;

    private final Object[] inputArray;
    private final Map<Integer, Object> outputMap;

    private Integer statePosition;

    /**
     * constructs a new tensorflow model.
     *
     * @param loader the loader (builder) for the model
     */
    public TensorflowModel(Loader loader) {
        this.interpreter = new Interpreter(new File(loader.path));
        for (int i = 0; i < this.interpreter.getInputTensorCount(); i++) {
            int[] shape = this.interpreter.getInputTensor(i).shape();
            int combinedShape = combineShape(shape);
            this.inputBuffers.add(
                  ByteBuffer.allocateDirect(combinedShape * loader.inputSize)
                        .order(ByteOrder.nativeOrder()));
        }
        for (int i = 0; i < this.interpreter.getOutputTensorCount(); i++) {
            int[] shape = this.interpreter.getOutputTensor(i).shape();
            int combinedShape = combineShape(shape);
            this.outputBuffers.add(
                  ByteBuffer.allocateDirect(combinedShape * loader.outputSize)
                        .order(ByteOrder.nativeOrder()));
        }

        this.inputSize = loader.inputSize;
        this.statePosition = loader.statePosition;
        this.inputArray = new Object[this.inputBuffers.size()];
        this.outputMap = new HashMap<>();
    }

    private int combineShape(int[] dims) {
        int product = 1;
        for (int dim : dims) {
            product *= dim;
        }
        return product;
    }

    /**
     * @return the byte size of the model's inputs.
     */
    public int getInputSize() {
        return inputSize;
    }

    /**
     * releases the tensorflow interpreter.
     */
    public void close() {
        this.interpreter.close();
    }

    /**
     * Get the input buffer at the specified index.
     *
     * @param index The index of the desired input buffer.
     * @return the input tensor buffer at the specified index.
     */
    public ByteBuffer inputs(int index) {
        return this.inputBuffers.get(index);
    }

    /**
     * @return the state tensor buffer
     */
    public ByteBuffer states() {
        if (this.statePosition == null) {
            return null;
        }
        return this.inputBuffers.get(this.statePosition);
    }

    /**
     * Get the output buffer at the specified index.
     *
     * @param index The index of the desired output buffer.
     * @return the output tensor buffer at the specified index.
     */
    public ByteBuffer outputs(int index) {
        return this.outputBuffers.get(index);
    }

    /**
     * executes the model using the attached buffers.
     */
    public void run() {
        for (ByteBuffer buffer : this.inputBuffers) {
            buffer.rewind();
        }
        for (ByteBuffer buffer : this.outputBuffers) {
            buffer.rewind();
        }

        for (int i = 0; i < this.inputBuffers.size(); i++) {
            this.inputArray[i] = this.inputBuffers.get(i);
        }
        for (int i = 0; i < this.outputBuffers.size(); i++) {
            this.outputMap.put(i, this.outputBuffers.get(i));
        }

        this.interpreter.runForMultipleInputsOutputs(
              this.inputArray,
              this.outputMap);

        if (this.statePosition != null) {
            ByteBuffer temp =
                  this.inputBuffers.remove((int) this.statePosition);
            ByteBuffer tempOutput =
                  this.outputBuffers.remove((int) this.statePosition);
            this.inputBuffers.add(this.statePosition, tempOutput);
            this.outputBuffers.add(this.statePosition, temp);
        }
        for (ByteBuffer buffer : this.inputBuffers) {
            buffer.rewind();
        }
        for (ByteBuffer buffer : this.outputBuffers) {
            buffer.rewind();
        }
    }

    /**
     * loader (builder) class for the tensorflow model.
     */
    public static class Loader {
        /**
         * tensor data types.
         */
        public enum DType {
            /**
             * 32-bit floating tensor.
             */
            FLOAT
        }

        private String path;
        private int inputSize;
        private int outputSize;
        private Integer statePosition = null;

        /**
         * initializes a new loader instance.
         */
        public Loader() {
            this.reset();
        }

        /**
         * resets the loader to the default state.
         *
         * @return this
         */
        public Loader reset() {
            this.path = null;
            this.inputSize = 4;
            this.outputSize = 4;
            this.statePosition = null;
            return this;
        }

        /**
         * sets the file system path to the TF-Lite model.
         *
         * @param value value to assign
         * @return this
         */
        public Loader setPath(String value) {
            this.path = value;
            return this;
        }

        /**
         * sets the position of the model's state tensor in its input array.
         *
         * @param position the position of the model's state tensor.
         * @return this
         */
        public Loader setStatePosition(int position) {
            this.statePosition = position;
            return this;
        }

        /**
         * loads the tensorflow model using the attached configuration.
         *
         * @return the new tensorflow model
         */
        public TensorflowModel load() {
            TensorflowModel model = new TensorflowModel(this);
            this.reset();
            return model;
        }
    }
}
