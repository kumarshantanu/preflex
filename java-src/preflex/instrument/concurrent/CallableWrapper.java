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

import java.util.concurrent.Callable;

import preflex.instrument.task.CallTask1;
import preflex.instrument.task.Wrapper;

public class CallableWrapper<V, ExecutionEvent> implements Callable<V> {

    private final Callable<V> orig;
    private final ConcurrentEventFactory<?, ?, ExecutionEvent> eventFactory;
    private final Wrapper<ExecutionEvent> wrapper;

    public CallableWrapper(Callable<V> task, ConcurrentEventFactory<?, ?, ExecutionEvent> eventFactory,
            Wrapper<ExecutionEvent> wrapper) {
        this.orig = task;
        this.eventFactory = eventFactory;
        this.wrapper = wrapper;
    }

    public Callable<V> getOrig() {
        return orig;
    }

    @Override
    public V call() throws Exception {
        ExecutionEvent event = eventFactory.callableExecutionEvent(orig);
        CallTask1<V, Exception> task = new CallTask1<V, Exception>() {
            @Override
            public V call() throws Exception {
                return orig.call();
            }
        };
        return wrapper.call(event, task, Exception.class);
    }

}
