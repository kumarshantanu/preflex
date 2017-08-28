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

import preflex.instrument.task.RunTask;
import preflex.instrument.task.Wrapper;

public class RunnableWrapper<ExecutionEvent> implements Runnable {

    private final Runnable orig;
    private final ConcurrentEventFactory<?, ?, ExecutionEvent> eventFactory;
    private final Wrapper<ExecutionEvent> wrapper;

    public RunnableWrapper(Runnable command, ConcurrentEventFactory<?, ?, ExecutionEvent> eventFactory,
            Wrapper<ExecutionEvent> wrapper) {
        this.orig = command;
        this.eventFactory = eventFactory;
        this.wrapper = wrapper;
    }

    public Runnable getOrig() {
        return orig;
    }

    @Override
    public void run() {
        ExecutionEvent event = eventFactory.runnableExecutionEvent(orig);
        RunTask task = new RunTask() {
            @Override
            public void run() {
                orig.run();
            }
        };
        wrapper.run(event, task);
    }

}
