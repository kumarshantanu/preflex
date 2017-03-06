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


/**
 * Basic statistics functions. Not for scientific use! They err in favor of speed over precision and accuracy.
 *
 */
public final class Stats {

    /**
     * Cannot instantiate utility class.
     */
    private Stats() { }

    /** Internal constant for 100%. */
    private static final int HUNDRED_PERCENT = 100;

    /**
     * Throw exception if the specified array is NULL, or empty.
     * @param array long array
     */
    private static void assertNotNull(final long[] array) {
        if (array == null) {
            throw new IllegalArgumentException("Argument array is NULL");
        }
    }

    /**
     * Return a sum of all elements in specified array.
     * @param array long array
     * @return      sum of all elements in the array argument
     */
    public static long sum(final long[] array) {
        assertNotNull(array);
        long result = 0;
        for (int i = 0; i < array.length; i++) {
            result += array[i];
        }
        return result;
    }

    /**
     * Compute the average of all element values in the specified array.
     * @param array long array
     * @return      average of all element values
     */
    public static double average(final long[] array) {
        assertNotNull(array);
        if (array.length == 0) {
            return 0;
        }
        return ((double) sum(array)) / array.length;
    }

    /**
     * Return the first element in an array, after verifying that the array is not empty.
     * @param array long array
     * @return      first element of the array
     */
    public static long first(final long[] array) {
        assertNotNull(array);
        if (array.length == 0) {
            return 0;
        }
        return array[0];
    }

    /**
     * Return the last element in an array, after verifying that the array is not empty.
     * @param array long array
     * @return      last element of the array
     */
    public static long last(final long[] array) {
        assertNotNull(array);
        if (array.length == 0) {
            return 0;
        }
        return array[array.length - 1];
    }

    /**
     * Median calculation as per the
     * <a href="http://en.wikipedia.org/wiki/Median#Easy_explanation_of_the_sample_median">Sample median method</a>.
     * @param sortedArray integer array sorted in ascending order
     * @return            median value
     */
    public static double median(final long[] sortedArray) {
        assertNotNull(sortedArray);
        if (sortedArray.length == 0) {
            return 0;
        }
        if (sortedArray.length == 1) {
            return sortedArray[0];
        }
        final int middle = sortedArray.length / 2;
        if (sortedArray.length % 2 == 0) {  // even number of elements?
            return ((double) sortedArray[middle - 1] + sortedArray[middle]) / 2;
        } else {
            return sortedArray[middle];
        }
    }

    /**
     * Percentile calculation as per the
     * <a href="http://en.wikipedia.org/wiki/Percentile#Definition_of_the_Nearest_Rank_method">Nearest Rank method</a>.
     * @param sortedArray integer array sorted in ascending order
     * @param percent     percent to compute percentile for
     * @return            percentile value
     */
    public static long percentile(final long[] sortedArray, final double percent) {
        assertNotNull(sortedArray);
        if (sortedArray.length == 0) {
            return 0;
        }
        if (percent < 0 || percent > HUNDRED_PERCENT) {
            throw new IllegalArgumentException("Invalid percentile: " + percent);
        }
        final long max = last(sortedArray);
        if (percent == HUNDRED_PERCENT) {
            return max;
        }
        final int rank = (int) Math.round((percent * sortedArray.length) / HUNDRED_PERCENT);
        final int rankIndex = rank - 1;
        if (rankIndex < 0) {
            return 0;
        }
        if (rankIndex >= sortedArray.length) {
            return max;
        }
        return sortedArray[rankIndex];
    }

}
