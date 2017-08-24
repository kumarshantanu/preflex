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

import preflex.instrument.task.CallTask;
import preflex.instrument.task.CallTask1;
import preflex.instrument.task.CallTask2;
import preflex.instrument.task.CallTask3;
import preflex.instrument.task.RunTask;
import preflex.instrument.task.Wrapper;

public class ExecutorServiceWrapper<ThreadPoolEvent, FutureEvent, ExecutionEvent> implements ExecutorService {

    private final ExecutorService orig;

    private final ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventFactory;
    private final Wrapper<ThreadPoolEvent> callableSubmitWrapper;
    private final Wrapper<ThreadPoolEvent> multipleSubmitWrapper;
    private final Wrapper<ThreadPoolEvent> runnableSubmitWrapper;
    private final Wrapper<ThreadPoolEvent> shutdownRequestWrapper;

    private final Wrapper<ExecutionEvent> callableExecutionWrapper;
    private final Wrapper<ExecutionEvent> runnableExecutionWrapper;
    private final Wrapper<FutureEvent>    futureCancelWrapper;
    private final Wrapper<FutureEvent>    futureResultWrapper;

    private final CallableDecorator<?>    callableDecorator;
    private final RunnableDecorator       runnableDecorator;

    public ExecutorServiceWrapper(ExecutorService threadPool,
            ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventFactory,
            ConcurrentTaskWrapper<ThreadPoolEvent, FutureEvent, ExecutionEvent> concurrentTaskWrappers,
            CallableDecorator<?> callableDecorator,
            RunnableDecorator runnableDecorator) {
        this.orig = threadPool;
        this.eventFactory = eventFactory;
        this.callableSubmitWrapper = concurrentTaskWrappers.callableSubmitWrapper;
        this.multipleSubmitWrapper = concurrentTaskWrappers.multipleSubmitWrapper;
        this.runnableSubmitWrapper = concurrentTaskWrappers.runnableSubmitWrapper;
        this.shutdownRequestWrapper = concurrentTaskWrappers.shutdownRequestWrapper;
        this.callableExecutionWrapper = concurrentTaskWrappers.callableExecuteWrapper;
        this.runnableExecutionWrapper = concurrentTaskWrappers.runnableExecuteWrapper;
        this.futureCancelWrapper = concurrentTaskWrappers.futureCancelWrapper;
        this.futureResultWrapper = concurrentTaskWrappers.futureResultWrapper;
        this.callableDecorator = callableDecorator;
        this.runnableDecorator = runnableDecorator;
    }

    @Override
    public void execute(final Runnable runnable) {
        final Runnable decoratedRunnable = runnableDecorator.wrapRunnable(runnable);
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionWrapper);
        final ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(decoratedRunnable);
        final RunTask task = new RunTask() {
            @Override
            public void run() {
                orig.execute(instrumentedTask);
            }
        };
        runnableSubmitWrapper.run(event, task);
    }

    @Override
    public void shutdown() {
        final ThreadPoolEvent event = eventFactory.shutdownEvent();
        final RunTask task = new RunTask() {
            @Override
            public void run() {
                orig.shutdown();
            }
        };
        shutdownRequestWrapper.run(event, task);
    }

    @Override
    public List<Runnable> shutdownNow() {
        final ThreadPoolEvent event = eventFactory.shutdownEvent();
        final CallTask<List<Runnable>> task = new CallTask<List<Runnable>>() {
            @Override
            public List<Runnable> call() {
                return orig.shutdownNow();
            }
        };
        return shutdownRequestWrapper.call(event, task);
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
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        final SharedContextCallable<?, T> decoratedTask = typedCallableDecorator.wrapCallable(callable);
        final CallableWrapper<T, ?> instrumentedTask = new CallableWrapper<>(decoratedTask,
                eventFactory, callableExecutionWrapper);
        final ThreadPoolEvent event = eventFactory.callableSubmissionEvent(decoratedTask);
        final CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                final Future<T> future = orig.submit(instrumentedTask);
                return new FutureWrapper<>(decoratedTask.wrapFuture(future),
                        eventFactory, futureCancelWrapper, futureResultWrapper);
            }
        };
        return callableSubmitWrapper.call(event, task);
    }

    @Override
    public <T> Future<T> submit(final Runnable runnable, final T result) {
        final SharedContextRunnable<?> decoratedRunnable = runnableDecorator.wrapRunnable(runnable);
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionWrapper);
        final ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(decoratedRunnable);
        final CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                final Future<T> future = orig.submit(instrumentedTask, result);
                @SuppressWarnings("unchecked")
                final Future<T> decoratedFuture = (Future<T>) decoratedRunnable.wrap(future);
                return new FutureWrapper<>(decoratedFuture,
                        eventFactory, futureCancelWrapper, futureResultWrapper);
            }
        };
        return runnableSubmitWrapper.call(event, task);
    }

    @Override
    public Future<?> submit(final Runnable runnable) {
        final SharedContextRunnable<?> decoratedRunnable = runnableDecorator.wrapRunnable(runnable);
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionWrapper);
        final ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(decoratedRunnable);
        final CallTask<Future<?>> task = new CallTask<Future<?>>() {
            @Override
            public Future<?> call() {
                final Future<?> future = orig.submit(instrumentedTask);
                @SuppressWarnings("unchecked")
                final Future<Object> futureObject = (Future<Object>) future;
                return new FutureWrapper<>(decoratedRunnable.wrap(futureObject),
                        eventFactory, futureCancelWrapper, futureResultWrapper);
            }
        };
        return runnableSubmitWrapper.call(event, task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        final int taskCount = tasks.size();
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        final List<SharedContextCallable<?, T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final SharedContextCallable<?, T> decoratedTask = typedCallableDecorator.wrapCallable(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionWrapper);
            decoratedTasks.add(decoratedTask);
            instrumentedTasks.add(eachInstrumentedTask);
        }
        final ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(decoratedTasks);
        final CallTask1<List<Future<T>>, InterruptedException> task =
                new CallTask1<List<Future<T>>, InterruptedException>() {
            @Override
            public List<Future<T>> call() throws InterruptedException {
                List<Future<T>> futures = orig.invokeAll(instrumentedTasks);
                List<Future<T>> futWrappers = new ArrayList<>(taskCount);
                int i = 0;
                for (Future<T> each: futures) {
                    final Future<T> instrumentedFuture = new FutureWrapper<>(
                            decoratedTasks.get(i).wrapFuture(each), eventFactory,
                            futureCancelWrapper, futureResultWrapper);
                    futWrappers.add(instrumentedFuture);
                    i++;
                }
                return futWrappers;
            }
        };
        return multipleSubmitWrapper.call(event, task, InterruptedException.class);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout,
            final TimeUnit unit) throws InterruptedException {
        final int taskCount = tasks.size();
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        final List<SharedContextCallable<?, T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final SharedContextCallable<?, T> decoratedTask = typedCallableDecorator.wrapCallable(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionWrapper);
            decoratedTasks.add(decoratedTask);
            instrumentedTasks.add(eachInstrumentedTask);
        }
        final ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(decoratedTasks);
        final CallTask1<List<Future<T>>, InterruptedException> task =
                new CallTask1<List<Future<T>>, InterruptedException>() {
            @Override
            public List<Future<T>> call() throws InterruptedException {
                List<Future<T>> futures = orig.invokeAll(instrumentedTasks, timeout, unit);
                List<Future<T>> futWrappers = new ArrayList<>(taskCount);
                int i = 0;
                for (Future<T> each: futures) {
                    final Future<T> instrumentedFuture = new FutureWrapper<>(
                            decoratedTasks.get(i).wrapFuture(each), eventFactory,
                            futureCancelWrapper, futureResultWrapper);
                    futWrappers.add(instrumentedFuture);
                    i++;
                }
                return futWrappers;
            }
        };
        return multipleSubmitWrapper.call(event, task, InterruptedException.class);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        final int taskCount = tasks.size();
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        final Collection<Callable<T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final Callable<T> decoratedTask = typedCallableDecorator.wrapCallable(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionWrapper);
            decoratedTasks.add(decoratedTask);
            instrumentedTasks.add(eachInstrumentedTask);
        }
        final ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(decoratedTasks);
        final CallTask2<T, InterruptedException, ExecutionException> task =
                new CallTask2<T, InterruptedException, ExecutionException>() {
            @Override
            public T call() throws InterruptedException, ExecutionException {
                return orig.invokeAny(instrumentedTasks);
            }
        };
        return multipleSubmitWrapper.call(event, task, InterruptedException.class, ExecutionException.class);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        final int taskCount = tasks.size();
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        final Collection<Callable<T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final Callable<T> decoratedTask = typedCallableDecorator.wrapCallable(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionWrapper);
            decoratedTasks.add(decoratedTask);
            instrumentedTasks.add(eachInstrumentedTask);
        }
        final ThreadPoolEvent event = eventFactory.callableCollectionSubmissionEvent(decoratedTasks);
        final CallTask3<T, InterruptedException, ExecutionException, TimeoutException> task =
                new CallTask3<T, InterruptedException, ExecutionException, TimeoutException>() {
            @Override
            public T call() throws InterruptedException, ExecutionException, TimeoutException {
                return orig.invokeAny(instrumentedTasks, timeout, unit);
            }
        };
        return multipleSubmitWrapper.call(event, task,
                InterruptedException.class, ExecutionException.class, TimeoutException.class);
    }

}
