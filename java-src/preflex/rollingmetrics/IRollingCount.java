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

/**
 * Rolling metrics API for storing count.
 *
 */
public interface IRollingCount {

    /** Store occurrence as a count. */
    void record();

    /** Reset all counter data. */
    void reset();

    /**
     * Get all rolling count data, including the current bucket/window.
     * @return all rolling count elements
     */
    long[] getAllElements();

    /**
     * Get all rolling count data, excluding the current bucket/window.
     * @return all rolling count elements except the current bucket/window
     */
    long[] getPreviousElements();

}
