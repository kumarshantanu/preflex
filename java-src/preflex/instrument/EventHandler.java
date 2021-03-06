/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 *   the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument;

public interface EventHandler {

    public static final EventHandler NOP = new EventHandler() {
        public void before()                 { /* do nothing */ }
        public void onReturn()               { /* do nothing */ }
        public void onResult(Object result)  { /* do nothing */ }
        public void onThrow(Exception error) { /* do nothing */ }
        public void after()                  { /* do nothing */ }
    };

    /**
     * Triggered before the code is executed.
     */
    public void before();

    /**
     * Triggered after a code ends without returning a value, throwing no exception.
     */
    public void onReturn();

    /**
     * Triggered after a code returning a value, throwing no exception.
     * @param result result value
     */
    public void onResult(Object result);

    /**
     * Triggered after a code throws exception.
     * @param error the exception
     */
    public void onThrow(Exception error);

    /**
     * Triggered finally, after everything is over.
     */
    public void after();

}
