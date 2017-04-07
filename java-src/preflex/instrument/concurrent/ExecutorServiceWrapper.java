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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import preflex.instrument.EventHandlerFactory;
import preflex.instrument.task.CallTask;
import preflex.instrument.task.CallTask1;
import preflex.instrument.task.CallTask2;
import preflex.instrument.task.CallTask3;
import preflex.instrument.task.InstrumentingWrapper;
import preflex.instrument.task.RunTask;

public class ExecutorServiceWrapper<ThreadPoolEvent, FutureEvent, ExecutionEvent> implements ExecutorService {

    private final ExecutorService orig;
    private final ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventFactory;
    private final InstrumentingWrapper<ThreadPoolEvent> wrapper;
    private final EventHandlerFactory<ExecutionEvent> executionEventHandlerFactory;
    private final EventHandlerFactory<FutureEvent> futureEventHandlerFactory;

    public ExecutorServiceWrapper(ExecutorService threadPool,
            ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventFactory,
            EventHandlerFactory<ThreadPoolEvent> threadPoolEventHandlerFactory,
            EventHandlerFactory<ExecutionEvent> executionEventHandlerFactory,
            EventHandlerFactory<FutureEvent> futureEventHandlerFactory) {
        this.orig = threadPool;
        this.eventFactory = eventFactory;
        this.wrapper = new InstrumentingWrapper<>(threadPoolEventHandlerFactory);
        this.executionEventHandlerFactory = executionEventHandlerFactory;
        this.futureEventHandlerFactory = futureEventHandlerFactory;
    }

    @Override
    public void execute(final Runnable command) {
        ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(command);
        RunTask task = new RunTask() {
            @Override
            public void run() {
                orig.execute(new RunnableWrapper<>(command, eventFactory, executionEventHandlerFactory));
            }
        };
        wrapper.run(event, task);
    }

    @Override
    public void shutdown() {
        ThreadPoolEvent event = eventFactory.shutdownEvent();
        RunTask task = new RunTask() {
            @Override
            public void run() {
                orig.shutdown();
            }
        };
        wrapper.run(event, task);
    }

    @Override
    public List<Runnable> shutdownNow() {
        ThreadPoolEvent event = eventFactory.shutdownEvent();
        CallTask<List<Runnable>> task = new CallTask<List<Runnable>>() {
            @Override
            public List<Runnable> call() {
                return orig.shutdownNow();
            }
        };
        return wrapper.call(event, task);
    }

    @Override
    public boolean isShutdown() {
        return orig.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return orig.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return orig.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> callable) {
        ThreadPoolEvent event = eventFactory.callableSubmissionEvent(callable);
        CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                Future<T> future = orig.submit(
                        new CallableWrapper<>(callable, eventFactory, executionEventHandlerFactory));
                return new FutureWrapper<T, FutureEvent>(future, eventFactory, futureEventHandlerFactory);
            }
        };
        return wrapper.call(event, task);
    }

    @Override
    public <T> Future<T> submit(final Runnable runnable, final T result) {
        ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(runnable);
        CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                Future<T> future = orig.submit(
                        new RunnableWrapper<>(runnable, eventFactory, executionEventHandlerFactory), result);
                return new FutureWrapper<T, FutureEvent>(future, eventFactory, futureEventHandlerFactory);
            }
        };
        return wrapper.call(event, task);
    }

    @Override
    public Future<?> submit(final Runnable runnable) {
        ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(runnable);
        CallTask<Future<?>> task = new CallTask<Future<?>>() {
            @Override
            public Future<?> call() {
                Future<?> future = orig.submit(
                        new RunnableWrapper<>(runnable, eventFactory, executionEventHandlerFactory));
                @SuppressWarnings("unchecked")
                Future<Object> futureObject = (Future<Object>) future;
                return new FutureWrapper<Object, FutureEvent>(futureObject, eventFactory, futureEventHandlerFactory);
            }
        };
        return wrapper.call(event, task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(tasks);
        CallTask1<List<Future<T>>, InterruptedException> task = new CallTask1<List<Future<T>>, InterruptedException>() {
            @Override
            public List<Future<T>> call() throws InterruptedException {
                List<CallableWrapper<T, ExecutionEvent>> callableWrappers = new ArrayList<>();
                for (Callable<T> each: tasks) {
                    callableWrappers.add(new CallableWrapper<>(each, eventFactory, executionEventHandlerFactory));
                }
                List<Future<T>> futures = orig.invokeAll(callableWrappers);
                List<Future<T>> futWrappers = new ArrayList<>();
                for (Future<T> each: futures) {
                    futWrappers.add(new FutureWrapper<T, FutureEvent>(each, eventFactory, futureEventHandlerFactory));
                }
                return futWrappers;
            }
        };
        return wrapper.call(event, task, InterruptedException.class);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout,
            final TimeUnit unit) throws InterruptedException {
        ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(tasks);
        CallTask1<List<Future<T>>, InterruptedException> task = new CallTask1<List<Future<T>>, InterruptedException>() {
            @Override
            public List<Future<T>> call() throws InterruptedException {
                List<CallableWrapper<T, ExecutionEvent>> callableWrappers = new ArrayList<>();
                for (Callable<T> each: tasks) {
                    callableWrappers.add(new CallableWrapper<>(each, eventFactory, executionEventHandlerFactory));
                }
                List<Future<T>> futures = orig.invokeAll(callableWrappers, timeout, unit);
                List<Future<T>> futWrappers = new ArrayList<>();
                for (Future<T> each: futures) {
                    futWrappers.add(new FutureWrapper<T, FutureEvent>(each, eventFactory, futureEventHandlerFactory));
                }
                return futWrappers;
            }
        };
        return wrapper.call(event, task, InterruptedException.class);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(tasks);
        CallTask2<T, InterruptedException, ExecutionException> task =
                new CallTask2<T, InterruptedException, ExecutionException>() {
            @Override
            public T call() throws InterruptedException, ExecutionException {
                return orig.invokeAny(tasks);
            }
        };
        return wrapper.call(event, task, InterruptedException.class, ExecutionException.class);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(tasks);
        CallTask3<T, InterruptedException, ExecutionException, TimeoutException> task =
                new CallTask3<T, InterruptedException, ExecutionException, TimeoutException>() {
            @Override
            public T call() throws InterruptedException, ExecutionException, TimeoutException {
                return orig.invokeAny(tasks, timeout, unit);
            }
        };
        return wrapper.call(event, task, InterruptedException.class, ExecutionException.class, TimeoutException.class);
    }

}
