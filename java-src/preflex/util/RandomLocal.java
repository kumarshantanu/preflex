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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Java (as of Java 8) we have ThreadLocal but not ProcessorLocal, which could have helped avoid contention in many
 * cases. As an alternative to ProcessorLocal, this class randomly selects an element from a pool proportional in size
 * to the number of CPU processors (maximum 64 by default).
 * The operations on the pool elements should be commutative, associative and likely inconsistency tolerant for the
 * arrangement to work.
 *
 * @param <T> type of the element
 */
public class RandomLocal<T> {

    private static final int MAX_DEFAULT_PROCESSORS = 64;

    private final List<T> coll;
    private final int length;

    public RandomLocal(List<T> coll) {
        if (coll == null || coll.isEmpty()) {
            throw new IllegalArgumentException("Input collection is " + (coll == null? "NULL": "empty"));
        }
        this.coll = Collections.unmodifiableList(new ArrayList<>(coll));
        this.length = this.coll.size();
    }

    public static int defaultShardCount() {
        return Math.min(Runtime.getRuntime().availableProcessors(), MAX_DEFAULT_PROCESSORS) * 2;
    }

    public static <T> RandomLocal<T> create(Callable<T> supplier) {
        return create(defaultShardCount(), supplier);
    }

    public static <T> RandomLocal<T> create(int count, Callable<T> supplier) {
        if (count <= 0) {
            throw new IllegalArgumentException("Expected input count to be a positive integer, but found " + count);
        }
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                list.add(supplier.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return new RandomLocal<T>(list);
    }

    public T get() {
        return coll.get(ThreadLocalRandom.current().nextInt(length));
    }

    public List<T> getAll() {
        return coll;
    }

}
