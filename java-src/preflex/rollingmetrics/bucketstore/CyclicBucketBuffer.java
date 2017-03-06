/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.rollingmetrics.bucketstore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import preflex.util.Pending;

/**
 * Cyclic buckets buffer that manages headIndex and its cycling in boundary.
 * <pre>
 * bucketIndex    arrayIndex
 *      7+-----------+ 0
 *       |           |
 *      8+-----------+ 1
 *       |           |
 *      9+-----------+ 2
 *       |           |
 *     10+-----------+ 3    ^  ^  ^                             ^  ^  ^
 *       |           |      |  |  |                             |  |  |
 *      0+-----------+ 4 (headIndex=4) ==> synchronized with latestEventID (monotonically increasing)
 *       |           |
 *      1+-----------+ 5                   bucketIndex = (arrayIndex - headIndex + bucketCount) % bucketCount
 *       |           |
 *      2+-----------+ 6                   arrayIndex = (bucketIndex + headIndex) % bucketCount
 *       |           |
 *      3+-----------+ 7
 *       |           |
 *      4+-----------+ 8
 *       |           |
 *      5+-----------+ 9
 *       |           |
 *      6+-----------+10
 *       |           |
 *     ~~+-----------+~~
 * </pre>
 * Note:
 * 1. `headArrayIndex` is a moving array index (0 <= `headArrayIndex` < `bucketCount`) where `bucketIndex` = 0
 * 2. `bucketIndex` is also a moving index because it is derived from `headArrayIndex`, valid in point of time
 * 3. `headArrayIndex` always moves from higher to lower except when it rolls over from 0 to `bucketCount` - 1
 * 4. `headArrayIndex` always moves up in synchronization with monotonically increasing `latestEventID`
 * 5. `latestEventID` is always updated (relative to its old value) in multiples of `bucketInterval`
 * 6. Buckets in the bucket-store are referenced using arrayIndex in CyclicBucketsBuffer
 */
public class CyclicBucketBuffer implements IReducibleCyclicBucketBuffer {

    /** Magic constant for "array index not found" scenario. */
    private static final int ARRAY_INDEX_NOT_FOUND = -1;

    /** Pool of all buffer IDs. */
    private static final AtomicLong BUFFER_ID_POOL = new AtomicLong();

    /** Unique ID of the buffer instance. Useful for debugging. */
    public final long bufferId = BUFFER_ID_POOL.getAndIncrement();

    /** Total number of buckets, including the head and the tail. */
    private final int bucketCount;

    /** Total number of possible event IDs mapped to a bucket. */
    private final int bucketInterval;

    /** Bucket storage. */
    private final IBucketStore buckets;

    /** Non-blocking, atomic executor. */
    private final Pending pending;

    /** Internal update lock. */
    private final Object updateLock = new Object();

    /** Zero based array-index for the head bucket, always 0 <= headArrayIndex < bucketCount. */
    private volatile int headArrayIndex = 0;

    /** Latest known event ID. */
    private volatile long latestEventID;

    public CyclicBucketBuffer(int bucketInterval, IBucketStore bucketStore, long latestEventID, Pending pending) {
        if (bucketInterval <= 0) {
            throw new IllegalArgumentException("Expected a positive bucketInterval, but found " + bucketInterval);
        }
        this.bucketCount = bucketStore.getBucketCount();
        this.bucketInterval = bucketInterval;
        this.buckets = bucketStore;
        this.pending = pending;
        this.latestEventID = latestEventID;
    }


    /** Given eventID, return (potentially out-of-range) bucketIndex. */
    private int findBucketIndex(long eventID) {
    	final long diff = latestEventID - eventID;
        final int remainder = (int) diff % bucketInterval;
        final int quotient = (int) diff / bucketInterval;
        if (remainder >= 0) {  // we ignore (1) zero remainder for negative diff, (2) all remainder for positive diff
            return quotient;
        } else /* remainder < 0, which implies diff is negative */ {
            return quotient - 1;
        }
    }

    private int bucket2ArrayIndex(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= bucketCount) {
            return ARRAY_INDEX_NOT_FOUND;
        }
        return (bucketIndex + headArrayIndex) % bucketCount;
    }

    @SuppressWarnings("unused")
    private int array2BucketIndex(int arrayIndex) {
        return (arrayIndex - headArrayIndex + bucketCount) % bucketCount;
    }

    private int syncHeadAndGetArrayIndex(long eventID) {
        final int computedBucketIndex = findBucketIndex(eventID);
        if (computedBucketIndex >= 0) {  // possibly the most common case (during heavy load)
            return bucket2ArrayIndex(computedBucketIndex);
        } else {  // bucketIndex was found to be negative
            synchronized (updateLock) {
                final int bucketIndex = findBucketIndex(eventID);  // re-compute due to race condition
                if (bucketIndex >= 0) {
                    return bucket2ArrayIndex(computedBucketIndex);
                } else if (bucketIndex == -1) { // possibly 2nd most common case (during load) after bucketIndex >= 0
                    if (--headArrayIndex < 0) {
                        headArrayIndex = bucketCount - 1; // roll over by one bucket
                    }
                    buckets.reset(headArrayIndex);
                    this.latestEventID += this.bucketInterval;  // bump by one bucket
                    return headArrayIndex;
                } else /* bucketIndex is negative here */ if (bucketIndex > -bucketCount) {
                    final int oldHead = headArrayIndex;
                    headArrayIndex += bucketIndex;
                    if (headArrayIndex < 0) {
                        headArrayIndex += bucketCount; // roll over by several buckets
                    }
                    for (int i = 0; i < bucketCount; i++) {
                        if ((headArrayIndex > oldHead && (i < oldHead || i >= headArrayIndex))  // after roll over
                                || (headArrayIndex < oldHead && (i < oldHead && i >= headArrayIndex))) {  // no roll over
                            buckets.reset(i);
                        }
                    }
                    this.latestEventID += (-bucketIndex) * (long) this.bucketInterval;  // bump by shifted buckets
                    return headArrayIndex;
                } else /* bucketIndex <= -bucketCount */ {
                    for (int i = 0; i < bucketCount; i++) {
                        buckets.reset(i);
                    }
                    this.headArrayIndex = 0;
                    this.latestEventID = eventID;  // reset to supplied eventID because everything zapped
                    return headArrayIndex; // we synced headIndex, so return bucketIndex 0
                }
            }
        }
    }

    private void recordInternal(int arrayIndex, long value) {
        if (arrayIndex >= 0 && arrayIndex < bucketCount) {  // ignore the out-of-bounds index values
            buckets.record(arrayIndex, value);
        }
    }

    @Override
    public void record(final long eventID, final long value) {
        final int bucketIndex = findBucketIndex(eventID);
        if (bucketIndex >= 0) {  // requires no sync?
            recordInternal(bucket2ArrayIndex(bucketIndex), value);  // process directly, not via pending
        } else {  // requires sync
            pending.runPending(new Runnable() {
                @Override
                public void run() {
                    final int arrayIndex = syncHeadAndGetArrayIndex(eventID);
                    recordInternal(arrayIndex, value);
                }
            });
        }
    }

    @Override
    public long[] reduce(List<long[]> colls) {
        return buckets.reduce(colls);
    }

    @Override
    public void reset(final long newLatestEventID) {
        pending.run(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < bucketCount; i++) {
                    buckets.reset(i);
                }
                // the following already happens atomically (linearizable via Pending) except reporting calls
                synchronized (updateLock) {
                    CyclicBucketBuffer.this.headArrayIndex = 0;
                    CyclicBucketBuffer.this.latestEventID = newLatestEventID;
                }
            }
        });
    }

    private int[] arrayIndices(boolean includeHead) {
        int[] arrayIndices = new int[bucketCount - (includeHead? 0: 1)];
        for (int i = 0, index = headArrayIndex + (includeHead? 0: 1); i < arrayIndices.length; i++, index++) {
            if (index >= bucketCount) {
                index = 0;
            }
            arrayIndices[i] = index;
        }
        return arrayIndices;
    }

    @Override
    public long[] getAllElements() {
        return buckets.getElements(arrayIndices(true));
    }

    @Override
    public long[] getAllElements(long latestEventID) {
        while (!pending.isEmpty()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        syncHeadAndGetArrayIndex(latestEventID);
        return buckets.getElements(arrayIndices(true));
    }

    @Override
    public long[] getTailElements() {
        return buckets.getElements(arrayIndices(false));
    }

    @Override
    public long[] getTailElements(long latestEventID) {
        while (!pending.isEmpty()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        syncHeadAndGetArrayIndex(latestEventID);
        int[] indices = arrayIndices(false);
        return buckets.getElements(indices);
    }

}
