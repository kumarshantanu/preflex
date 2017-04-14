/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 *   the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument.concurrent;

import preflex.instrument.SharedContext;

public class DefaultDecoratedRunnable<T> extends SharedContext<T> implements Runnable {

    private final Runnable runnable;

    public DefaultDecoratedRunnable(Runnable runnable, T context) {
        super(context);
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }

}
