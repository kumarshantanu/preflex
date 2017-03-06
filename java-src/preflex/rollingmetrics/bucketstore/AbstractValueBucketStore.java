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

import java.util.concurrent.atomic.AtomicLongArray;

public abstract class AbstractValueBucketStore implements IBucketStore {

    protected final AtomicLongArray bucketElements;

    public AbstractValueBucketStore(int bucketCount) {
        this.bucketElements = new AtomicLongArray(bucketCount);
    }

    @Override
    public int getBucketCount() {
        return bucketElements.length();
    }

    @Override
    public void reset(int bucketIndex) {
        bucketElements.set(bucketIndex, 0);
    }

    @Override
    public long[] getElements(int[] indices) {
        final long[] result = new long[indices.length];
        for (int i = 0; i < indices.length; i++) {
            final int bucketIndex = indices[i];
            result[i] = bucketElements.get(bucketIndex);
        }
        return result;
    }

}
