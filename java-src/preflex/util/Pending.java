/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.util;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A non blocking, fair, flood tolerant, atomic executor of tasks. Useful for dealing with pending actions resulting
 * from lock contention.
 *
 */
public class Pending {

    /** Default timeout (nanoseconds) when clearing pending actions. */
    public static final long DEFAULT_PENDING_TIMEOUT_NANOS = 100;

    /** Default burst size for clearing pending actions before checking for timeout. */
    public static final int DEFAULT_PENDING_BURST_SIZE = 500;

    public static final int DEFAULT_SOFT_FLOOD_LEVEL = 4096;
    public static final int DEFAULT_HARD_FLOOD_LEVEL = 8192;

    public static final int DEFAULT_LOCAL_FLOOD_LEVEL = 512;
    public static final int DEFAULT_LOCAL_FLUSH_LEVEL = 128;

    public static final FloodHandler DEFAULT_SOFT_FLOOD_HANDLER = new FloodHandler() {
        @Override
        public void handle(final Pending pending, final Runnable action) {
            pending.samplingRunPending(/* percent */ 1, action);
        }
    };

    public static final FloodHandler DEFAULT_HARD_FLOOD_HANDLER = new FloodHandler() {
        @Override
        public void handle(final Pending pending, final Runnable action) {
            // drop actions
        }
    };

    /** Duration (nanoseconds) to spend on clearing pending actions. */
    private final long pendingTimeoutNanos;

    /** Number of pending actions to clear before checking for timeout. */
    private final int pendingBurstSize;

    private final int softFloodLevel;
    private final int hardFloodLevel;

    private final int localFloodLevel;
    private final int localFlushLevel;

    private final FloodHandler softFloodHandler;
    private final FloodHandler hardFloodHandler;

    public interface FloodHandler {
        void handle(Pending pending, Runnable action);
    }

    /** Provides mutex, useful to avoid contention. */
    private final ReentrantLock updateLock = new ReentrantLock(true);

    /** Actions pending in local queue until they reach the coordinates queue. */
    private final ThreadLocal<Queue<Runnable>> threadLocalQueue = new ThreadLocal<Queue<Runnable>>() {
        @Override
        protected Queue<Runnable> initialValue() {
            return new LinkedList<Runnable>();
        }
    };

    /** Pending actions. */
    private final BlockingQueue<Runnable> actionQueue = new LinkedBlockingQueue<Runnable>();

    public Pending() {
        this(DEFAULT_PENDING_TIMEOUT_NANOS, DEFAULT_PENDING_BURST_SIZE,
                DEFAULT_SOFT_FLOOD_LEVEL, DEFAULT_SOFT_FLOOD_HANDLER,
                DEFAULT_HARD_FLOOD_LEVEL, DEFAULT_HARD_FLOOD_HANDLER,
                DEFAULT_LOCAL_FLOOD_LEVEL, DEFAULT_LOCAL_FLUSH_LEVEL);
    }

    public Pending(final long pendingTimeoutNanos, final int pendingBurstSize,
            final int softFloodLevel, final FloodHandler softFloodHandler,
            final int hardFloodLevel, final FloodHandler hardFloodHandler,
            final int localFloodLevel, final int localFlushlevel) {
        this.pendingTimeoutNanos = pendingTimeoutNanos;
        this.pendingBurstSize = pendingBurstSize;
        this.softFloodLevel = softFloodLevel;
        this.hardFloodLevel = hardFloodLevel;
        this.softFloodHandler = softFloodHandler;
        this.hardFloodHandler = hardFloodHandler;
        this.localFloodLevel = localFloodLevel;
        this.localFlushLevel = localFlushlevel;
    }

    /**
     * Return true if pending actions exist, false otherwise.
     * @return true if pending actions exist, false otherwise
     */
    public boolean isEmpty() {
        return actionQueue.isEmpty();
    }

    /**
     * Execute action without blocking for a lock. Try to run without a queue first.
     * @param action action to execute
     */
    public void run(final Runnable action) {
        try {
            if (updateLock.tryLock(0, TimeUnit.SECONDS)) {
                if (actionQueue.isEmpty()) {
                    try {
                        action.run();
                    } finally {
                        updateLock.unlock();
                    }
                } else {
                    runPending(action);
                }
            } else {
                runPending(action);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            runPending(action);
        }
    }

    /**
     * Execute action without blocking for a lock. Run with a queue.
     * @param action action to execute
     */
    public void runPending(final Runnable action) {
        final int pendingCount = actionQueue.size();
        if (pendingCount < softFloodLevel) {
            //actionQueue.add(action);  // add directly to the shared queue (contends for CPU under high concurrency)
            runLocalPending(action);
            clearPending();
        } else if (pendingCount > hardFloodLevel) {
            hardFloodHandler.handle(this, action);
        } else {
            softFloodHandler.handle(this, action);
        }
    }

    /**
     * Sample over a 'percent' threshold to enqueue action for execution.
     * @param percent sampling percent
     * @param action
     */
    private void samplingRunPending(final int percent, final Runnable action) {
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();
        if (tlr.nextInt(100) < percent) {
            runPending(action);
        }
    }

    /**
     * Try to add action to pending queue without blocking.
     * @param action action to be performed
     */
    protected void runLocalPending(final Runnable action) {
        final Queue<Runnable> localQueue = threadLocalQueue.get();
        if (localQueue.isEmpty()) {
            try {
                if (actionQueue.offer(action, 0, TimeUnit.SECONDS)) {
                    return;
                } else {
                    localQueue.add(action);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                localQueue.add(action);
            }
        } else {
            localQueue.add(action);
            final int localQueueLength = localQueue.size();
            // flush local queue if reached threshold
            if (localQueueLength >= localFloodLevel) {
                // NOTE: We are dealing with thread-local queue, so the following instructions are atomic
                actionQueue.addAll(localQueue);
                localQueue.clear();
            } else if (localQueueLength % localFlushLevel == 0) { // queue has non-zero actions, try incremental flush
                try {
                    // NOTE: We are dealing with thread-local queue, so the following instructions are atomic
                    while ((!localQueue.isEmpty()) && actionQueue.offer(localQueue.peek(), 0, TimeUnit.SECONDS)) {
                        localQueue.remove();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // do nothing, because could not add any more elements to action queue at this time
                }
            }
        }
    }

    /**
     * Clear pending actions until timeout.
     */
    private void clearPending() {
        try {
            if (updateLock.tryLock(0, TimeUnit.SECONDS)) {
                try {
                    final long start = System.nanoTime();
                    TIMEOUT_LOOP: do {
                        for (int i = 0; i < pendingBurstSize; i++) {
                            final Runnable r = actionQueue.poll();
                            if (r != null) {
                                r.run();  // if it throws RuntimeException, we just bubble the exception up
                            } else {
                                break TIMEOUT_LOOP;
                            }
                        }
                    } while (System.nanoTime() - start < pendingTimeoutNanos);
                } finally {
                    updateLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ':' + "{actions=" + actionQueue.size() +
                ", localActions=" + threadLocalQueue.get().size() + "}";
    }

}
