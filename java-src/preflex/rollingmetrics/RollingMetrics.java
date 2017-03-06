/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.rollingmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import preflex.rollingmetrics.bucketstore.CyclicBucketBuffer;
import preflex.rollingmetrics.bucketstore.IBucketStore;
import preflex.rollingmetrics.bucketstore.ICyclicBucketBuffer;
import preflex.rollingmetrics.bucketstore.IReducibleCyclicBucketBuffer;
import preflex.rollingmetrics.bucketstore.MaxBucketStore;
import preflex.rollingmetrics.bucketstore.StoringBucketStore;
import preflex.rollingmetrics.bucketstore.SummingBucketStore;
import preflex.util.Pending;
import preflex.util.RandomLocal;

/**
 * Utility class for creating rolling metrics objects.
 *
 */
public final class RollingMetrics {

    /** Private constructor to avoid instantiation. */
    private RollingMetrics() { }

    private static <T> T get(Callable<T> supplier) {
        try {
            return supplier.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Internal method to wrap a summing {@link IRollingRecord} instance into a {@link IRollingCount} instance.
     * @param rollingSum the "summing" {@link IRollingRecord} instance
     * @return           wrapper {@link IRollingCount} instance
     */
    private static IRollingCount createRollingCount(final IRollingRecord rollingSum) {
        return new IRollingCount() {
            @Override
            public void reset() {
                rollingSum.reset();
            }

            @Override
            public void record() {
                rollingSum.record(1);
            }

            @Override
            public long[] getPreviousElements() {
                return rollingSum.getPreviousElements();
            }

            @Override
            public long[] getAllElements() {
                return rollingSum.getAllElements();
            }
        };
    }


    /**
     * Internal method to wrap a rolling token-bucket into a {@link IRollingRecord} instance.
     * @param ratb                  the {@link CyclicBucketBuffer} instance
     * @param latestEventIdSupplier supplier of latest event ID
     * @return                      wrapper {@link IRollingRecord} instance
     */
    private static IRollingRecord createRollingMetrics(final ICyclicBucketBuffer ratb,
            final Callable<Long> latestEventIdSupplier) {
        return new IRollingRecord() {
            @Override
            public void reset() {
                ratb.reset(get(latestEventIdSupplier));
            }

            @Override
            public void record(final long value) {
                ratb.record(get(latestEventIdSupplier), value);
            }

            @Override
            public long[] getPreviousElements() {
                return ratb.getTailElements(get(latestEventIdSupplier));
            }

            @Override
            public long[] getAllElements() {
                return ratb.getAllElements(get(latestEventIdSupplier));
            }
        };
    }


    // -------------------
    // Public API: Generic
    // -------------------


    public static final Callable<Long> MILLI_TIME_SUPPLIER = new Callable<Long>() {
        @Override
        public Long call() throws Exception {
            return System.currentTimeMillis();
        }
    };


    public static final Callable<Long> NANO_TIME_SUPPLIER = new Callable<Long>() {
        @Override
        public Long call() throws Exception {
            return System.nanoTime();
        }
    };


    /**
     * Given bucket count and event-IDs-per-bucket count, create a {@link IRollingCount} instance that counts
     * recording-occurrences.
     * @param bucketCount           number of buckets to create
     * @param bucketInterval        difference between min (inclusive) and max (inclusive) event ID per bucket
     * @param latestEventIdSupplier supplier of the latest event ID
     * @param shardCount            number of shards to create to split recording load (0 = auto-detect)
     * @return                      an {@link IRollingCount} instance
     */
    public static IRollingCount createRollingCount(final int bucketCount, final int bucketInterval,
            final Callable<Long> latestEventIdSupplier, int shardCount) {
        return createRollingCount(createRollingSum(bucketCount, bucketInterval, latestEventIdSupplier, shardCount));
    }


    /**
     * Given bucket count and event-IDs-per-bucket count, create a {@link IRollingRecord} instance that keeps only the
     * highest recorded numbers and throws away the rest.
     * @param bucketCount           number of buckets to create
     * @param bucketInterval        difference between min (inclusive) and max (inclusive) events per bucket
     * @param latestEventIdSupplier supplier of the latest event ID
     * @param shardCount            number of shards to create to split recording load (0 = auto-detect)
     * @return                      an {@link IRollingRecord} instance
     */
    public static IRollingRecord createRollingMax(final int bucketCount, final int bucketInterval,
            final Callable<Long> latestEventIdSupplier, int shardCount) {
        final Callable<IBucketStore> bucketStoreFactory = maxBucketsFactory(bucketCount);
        Callable<IReducibleCyclicBucketBuffer> cyclicBufferSupplier = new Callable<IReducibleCyclicBucketBuffer>() {
            @Override
            public IReducibleCyclicBucketBuffer call() throws Exception {
                return cyclicBucketBuffer(bucketInterval, bucketStoreFactory, get(latestEventIdSupplier));
            }
        };
        return createRollingMetrics(shardedCyclicBucketBuffer(cyclicBufferSupplier, shardCount), latestEventIdSupplier);
    }


    /**
     * Given bucket count and event-IDs-per-bucket count, create a {@link IRollingRecord} instance that sums the
     * recorded numbers.
     * @param bucketCount           number of buckets to create
     * @param bucketInterval        difference between min (inclusive) and max (inclusive) events per bucket
     * @param latestEventIdSupplier supplier of the latest event ID
     * @param shardCount            number of shards to create to split recording load (0 = auto-detect)
     * @return                      an {@link IRollingRecord} instance
     */
    public static IRollingRecord createRollingSum(final int bucketCount, final int bucketInterval,
            final Callable<Long> latestEventIdSupplier, int shardCount) {
        final Callable<IBucketStore> bucketStoreFactory = summingBucketsFactory(bucketCount);
        Callable<IReducibleCyclicBucketBuffer> cyclicBufferSupplier = new Callable<IReducibleCyclicBucketBuffer>() {
            @Override
            public IReducibleCyclicBucketBuffer call() throws Exception {
                return cyclicBucketBuffer(bucketInterval, bucketStoreFactory, get(latestEventIdSupplier));
            }
        };
        return createRollingMetrics(shardedCyclicBucketBuffer(cyclicBufferSupplier, shardCount), latestEventIdSupplier);
    }


    /**
     * Given bucket count and event-IDs-per-bucket count, create a {@link IRollingRecord} instance that stores the
     * recorded numbers. When events exceed bucket capacity, older elements are overwritten by newer; in effect only
     * max last N (bucketSize) elements are stored per bucket.
     * @param bucketCount           number of buckets to create
     * @param bucketInterval        difference between min (inclusive) and max (inclusive) events per bucket
     * @param bucketSize            number of elements (capacity) per bucket
     * @param latestEventIdSupplier supplier of the latest event ID
     * @param shardCount            number of shards to create to split recording load (0 = auto-detect)
     * @return                      an {@link IRollingRecord} instance
     */
    public static IRollingRecord createRollingStore(final int bucketCount, final int bucketInterval,
            final int bucketSize, final Callable<Long> latestEventIdSupplier, int shardCount) {
        final Callable<IBucketStore> bucketStoreFactory = storingBucketsFactory(bucketCount, bucketSize);
        Callable<IReducibleCyclicBucketBuffer> cyclicBufferSupplier = new Callable<IReducibleCyclicBucketBuffer>() {
            @Override
            public IReducibleCyclicBucketBuffer call() throws Exception {
                return cyclicBucketBuffer(bucketInterval, bucketStoreFactory, get(latestEventIdSupplier));
            }
        };
        return createRollingMetrics(shardedCyclicBucketBuffer(cyclicBufferSupplier, shardCount), latestEventIdSupplier);
    }

    // ---------- rolling metrics buckets ----------

    /**
     * Return a factory of buckets that only stores the maximum integer value in a bucket.
     * @param bucketCount number of buckets
     * @return            factory of buckets
     */
    public static Callable<IBucketStore> maxBucketsFactory(final int bucketCount) {
        return new Callable<IBucketStore>() {
            @Override
            public IBucketStore call() throws Exception {
                return new MaxBucketStore(bucketCount);
            }
        };
    }

    /**
     * Return a factory of buckets that only stores the sum of integer values in a bucket.
     * @param bucketCount number of buckets
     * @return            factory of buckets
     */
    public static Callable<IBucketStore> summingBucketsFactory(final int bucketCount) {
        return new Callable<IBucketStore>() {
            @Override
            public IBucketStore call() throws Exception {
                return new SummingBucketStore(bucketCount);
            }
        };
    }

    /**
     * Return a factory of buckets that only stores the integer values in a bucket.
     * @param bucketCount number of buckets
     * @return            factory of buckets
     */
    public static Callable<IBucketStore> storingBucketsFactory(final int bucketCount, final int bucketCapacity) {
        return new Callable<IBucketStore>() {
            @Override
            public IBucketStore call() throws Exception {
                return new StoringBucketStore(bucketCount, bucketCapacity);
            }
        };
    }

    // ---------- rolling metrics store ----------

    public static IReducibleCyclicBucketBuffer cyclicBucketBuffer(int bucketInterval,
            Callable<? extends IBucketStore> bucketStoreFactory, long latestEventID) {
        return new CyclicBucketBuffer(bucketInterval, get(bucketStoreFactory), latestEventID, new Pending());
    }

    /**
     * Create a sharded (striped) version of {@link ICyclicBucketBuffer} where the write load (update) is distributed
     * across a bunch of shards. Note that shards may be out of sync - coordinated synchronization is enforced at the
     * time of reading the recorded result.
     * @param supplier   supplier {@link IReducibleCyclicBucketBuffer} instance for each shard
     * @param shardCount number of shards, 0 implies auto-detect, positive integer implies actual shard count
     * @return           sharded instance of {@link ICyclicBucketBuffer}
     * @throws {{@link IllegalArgumentException} when shardCount is a negative integer
     */
    public static ICyclicBucketBuffer shardedCyclicBucketBuffer(
            Callable<? extends IReducibleCyclicBucketBuffer> supplier, int shardCount) {
        if (shardCount < 0) {
            throw new IllegalArgumentException(
                    "Expected 'shardCount' to be 0 (auto-detect) or a positive integer, but found " + shardCount);
        }
        if (shardCount == 1) {
            return get(supplier);
        }
        final RandomLocal<? extends IReducibleCyclicBucketBuffer> shards = RandomLocal.create(
                shardCount == 0? RandomLocal.defaultShardCount(): shardCount, supplier);
        return new ICyclicBucketBuffer() {
            @Override
            public void reset(long newLatestEventID) {
                for (ICyclicBucketBuffer buffer: shards.getAll()) {
                    buffer.reset(newLatestEventID);
                }
            }

            @Override
            public void record(long eventID, long value) {
                shards.get().record(eventID, value);
            }

            @Override
            public long[] getTailElements(long latestEventID) {
                final List<? extends IReducibleCyclicBucketBuffer> allShards = shards.getAll();
                if (allShards.isEmpty()) {
                    return new long[0];
                } else {
                    final List<long[]> colls = new ArrayList<>(allShards.size());
                    for (final ICyclicBucketBuffer eachBuffer: allShards) {
                        long[] elems = eachBuffer.getTailElements(latestEventID);
                        colls.add(elems);
                    }
                    return allShards.get(0).reduce(colls);
                }
            }

            @Override
            public long[] getTailElements() {
                final List<? extends IReducibleCyclicBucketBuffer> allShards = shards.getAll();
                if (allShards.isEmpty()) {
                    return new long[0];
                } else {
                    final List<long[]> colls = new ArrayList<>(allShards.size());
                    for (final ICyclicBucketBuffer eachBuffer: allShards) {
                        colls.add(eachBuffer.getTailElements());
                    }
                    return allShards.get(0).reduce(colls);
                }
            }

            @Override
            public long[] getAllElements(long latestEventID) {
                List<? extends IReducibleCyclicBucketBuffer> allShards = shards.getAll();
                if (allShards.isEmpty()) {
                    return new long[0];
                } else {
                    final List<long[]> colls = new ArrayList<>(allShards.size());
                    for (final ICyclicBucketBuffer eachBuffer: allShards) {
                        colls.add(eachBuffer.getAllElements(latestEventID));
                    }
                    return allShards.get(0).reduce(colls);
                }
            }

            @Override
            public long[] getAllElements() {
                List<? extends IReducibleCyclicBucketBuffer> allShards = shards.getAll();
                if (allShards.isEmpty()) {
                    return new long[0];
                } else {
                    final List<long[]> colls = new ArrayList<>(allShards.size());
                    for (final ICyclicBucketBuffer eachBuffer: allShards) {
                        colls.add(eachBuffer.getAllElements());
                    }
                    return allShards.get(0).reduce(colls);
                }
            }
        };
    }

}
