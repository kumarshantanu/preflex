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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

public class StoringBucketStore implements IBucketStore {

    /** Max number of elements per bucket. */
    private final int bucketCapacity;
    private final AtomicLongArray bucketElements;
    private final AtomicIntegerArray bucketLengths;

    public StoringBucketStore(int bucketCount, int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
        this.bucketElements = new AtomicLongArray(bucketCount * bucketCapacity);
        this.bucketLengths = new AtomicIntegerArray(bucketCount);
    }

    @Override
    public int getBucketCount() {
        return bucketLengths.length();
    }

    @Override
    public void record(int bucketIndex, long value) {
        int offset = bucketIndex * bucketCapacity + (bucketLengths.getAndIncrement(bucketIndex) % bucketCapacity);
        bucketElements.set(offset, value);
    }

    @Override
    public void reset(int bucketIndex) {
        bucketLengths.set(bucketIndex, 0);
    }

    @Override
    public long[] getElements(int[] indices) {
        final long[] coll = new long[bucketElements.length()];
        int dest = 0;
        for (int i = 0; i < indices.length; i++) {
            final int bucketIndex = indices[i];
            final int baseOffset = bucketIndex * bucketCapacity;
            final int nElements = Math.min(bucketLengths.get(bucketIndex), bucketCapacity);
            for (int j = 0; j < nElements; j++) {
                coll[dest++] = bucketElements.get(baseOffset + j);
            }
        }
        return Arrays.copyOfRange(coll, 0, dest);
    }

    @Override
    public long[] reduce(List<long[]> colls) {
        int destSize = 0;
        for (final long[] each: colls) {
            destSize += each.length;
        }
        final long[] dest = new long[destSize];
        int destPos = 0;
        for (final long[] each: colls) {
            System.arraycopy(each, 0, dest, destPos, each.length);
            destPos += each.length;
        }
        return dest;
    }

}
