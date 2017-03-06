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

public class SummingBucketStore extends AbstractValueBucketStore {

    public SummingBucketStore(int bucketCount) {
        super(bucketCount);
    }

    @Override
    public void record(int bucketIndex, long value) {
        bucketElements.addAndGet(bucketIndex, value);
    }

    @Override
    public long[] reduce(List<long[]> colls) {
        if (colls.isEmpty()) {
            return new long[0];
        }
        final long[] result = new long[colls.get(0).length];
        for (final long[] other: colls) {
            if (result.length != other.length) {
                throw new IllegalArgumentException("Expected other array to be of size " + result.length +
                        " but found " + other.length);
            }
            for (int i = 0; i < result.length; i++) {
                result[i] += other[i];
            }
        }
        return result;
    }

}
