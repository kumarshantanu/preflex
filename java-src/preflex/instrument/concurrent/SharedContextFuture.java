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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import preflex.instrument.SharedContext;

public class SharedContextFuture<T, V> extends SharedContext<T> implements Future<V> {

    private final Future<V> orig;

    public SharedContextFuture(Future<V> future, T context) {
        super(context);
        this.orig = future;
	}

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return orig.cancel(mayInterruptIfRunning);
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return orig.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return orig.get(timeout, unit);
    }

    @Override
    public boolean isCancelled() {
        return orig.isCancelled();
    }

    @Override
    public boolean isDone() {
        return orig.isDone();
    }

}
