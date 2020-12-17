package io.spokestack.spokestack.util;

import android.os.Handler;
import android.os.Looper;

/**
 * A generic task handler that executes {@code Runnable}s on either the current
 * thread or the application's main thread.
 */
public final class TaskHandler {
    private final boolean mainThread;
    private Handler handler;

    /**
     * Initialize a new task handler.
     *
     * @param runOnMainThread {@code true} if tasks submitted to this handler
     *                        should run on the application's main {@code
     *                        Looper}.
     */
    public TaskHandler(boolean runOnMainThread) {
        this.mainThread = runOnMainThread;
    }

    /**
     * Execute the task, either by submitting it to the main {@code Looper} or
     * running it on the current thread.
     *
     * @param task The task to run.
     */
    public void run(Runnable task) {
        if (this.mainThread) {
            // lazy initialization to avoid stubbing errors during testing
            if (this.handler == null) {
                this.handler = new Handler(Looper.getMainLooper());
            }
            this.handler.post(task);
        } else {
            task.run();
        }
    }
}
