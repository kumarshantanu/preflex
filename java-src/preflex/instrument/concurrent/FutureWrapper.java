/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import preflex.instrument.EventHandlerFactory;
import preflex.instrument.task.CallTask;
import preflex.instrument.task.CallTask2;
import preflex.instrument.task.CallTask3;
import preflex.instrument.task.InstrumentingWrapper;

public class FutureWrapper<V, FutureEvent> implements Future<V> {

    private final Future<V> orig;
    private final ConcurrentEventFactory<?, FutureEvent, ?> eventFactory;
    private final InstrumentingWrapper<FutureEvent> wrapper;

    public FutureWrapper(Future<V> future, ConcurrentEventFactory<?, FutureEvent, ?> eventFactory,
            EventHandlerFactory<FutureEvent> eventHandlerFactory) {
        this.orig = future;
        this.eventFactory = eventFactory;
        this.wrapper = new InstrumentingWrapper<>(eventHandlerFactory);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        FutureEvent event = eventFactory.cancellationEvent();
        CallTask<Boolean> task = new CallTask<Boolean>() {
            @Override
            public Boolean call() {
                return orig.cancel(mayInterruptIfRunning);
            }
        };
        return wrapper.call(event, task);
    }

    @Override
    public boolean isCancelled() {
        return orig.isCancelled();
    }

    @Override
    public boolean isDone() {
        return orig.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        FutureEvent event = eventFactory.resultFetchEvent();
        CallTask2<V, InterruptedException, ExecutionException> task =
                new CallTask2<V, InterruptedException, ExecutionException>() {
            @Override
            public V call() throws InterruptedException, ExecutionException {
                return orig.get();
            }
        };
        return wrapper.call(event, task, InterruptedException.class, ExecutionException.class);
    }

    @Override
    public V get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        FutureEvent event = eventFactory.resultFetchEvent();
        CallTask3<V, InterruptedException, ExecutionException, TimeoutException> task =
                new CallTask3<V, InterruptedException, ExecutionException, TimeoutException>() {
            @Override
            public V call() throws InterruptedException, ExecutionException, TimeoutException {
                return orig.get(timeout, unit);
            }
        };
        return wrapper.call(event, task, InterruptedException.class, ExecutionException.class, TimeoutException.class);
    }

}
