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
    private final InstrumentingWrapper<ThreadPoolEvent> callableSubmitWrapper;
    private final InstrumentingWrapper<ThreadPoolEvent> multipleSubmitWrapper;
    private final InstrumentingWrapper<ThreadPoolEvent> runnableSubmitWrapper;
    private final InstrumentingWrapper<ThreadPoolEvent> shutdownRequestWrapper;

    private final EventHandlerFactory<ExecutionEvent> callableExecutionEventHandlerFactory;
    private final EventHandlerFactory<ExecutionEvent> runnableExecutionEventHandlerFactory;
    private final EventHandlerFactory<FutureEvent>    futureCancelEventHandlerFactory;
    private final EventHandlerFactory<FutureEvent>    futureResultEventHandlerFactory;

    private final CallableDecorator<?>    callableDecorator;
    private final RunnableDecorator       runnableDecorator;
    private final FutureDecorator<?>      futureDecorator;
    private final FutureDecorator<Object> futureDecoratorObject;

    public ExecutorServiceWrapper(ExecutorService threadPool,
            ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventFactory,
            ConcurrentEventHandlerFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> eventHandlerFactories,
            CallableDecorator<?> callableDecorator,
            RunnableDecorator runnableDecorator,
            FutureDecorator<?> futureDecorator) {
        this.orig = threadPool;
        this.eventFactory = eventFactory;
        this.callableSubmitWrapper = new InstrumentingWrapper<>(eventHandlerFactories.callableSubmitHandlerFactory);
        this.multipleSubmitWrapper = new InstrumentingWrapper<>(eventHandlerFactories.multipleSubmitHandlerFactory);
        this.runnableSubmitWrapper = new InstrumentingWrapper<>(eventHandlerFactories.runnableSubmitHandlerFactory);
        this.shutdownRequestWrapper = new InstrumentingWrapper<>(eventHandlerFactories.shutdownRequestHandlerFactory);
        this.callableExecutionEventHandlerFactory = eventHandlerFactories.callableExecuteHandlerFactory;
        this.runnableExecutionEventHandlerFactory = eventHandlerFactories.runnableExecuteHandlerFactory;
        this.futureCancelEventHandlerFactory = eventHandlerFactories.futureCancelHandlerFactory;
        this.futureResultEventHandlerFactory = eventHandlerFactories.futureResultHandlerFactory;
        this.callableDecorator = callableDecorator;
        this.runnableDecorator = runnableDecorator;
        this.futureDecorator = futureDecorator;
        @SuppressWarnings("unchecked")
        final FutureDecorator<Object> futureDecoratorObject = (FutureDecorator<Object>) futureDecorator;
        this.futureDecoratorObject = futureDecoratorObject;
    }

    @Override
    public void execute(final Runnable runnable) {
        final Runnable decoratedRunnable = runnableDecorator.wrap(runnable);
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionEventHandlerFactory);
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
        @SuppressWarnings("unchecked")
        final FutureDecorator<T> typedFutureDecorator = (FutureDecorator<T>) futureDecorator;
        final Callable<T> decoratedTask = typedCallableDecorator.wrap(callable);
        final CallableWrapper<T, ?> instrumentedTask = new CallableWrapper<>(decoratedTask,
                eventFactory, callableExecutionEventHandlerFactory);
        final ThreadPoolEvent event = eventFactory.callableSubmissionEvent(decoratedTask);
        final CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                final Future<T> future = orig.submit(instrumentedTask);
                return new FutureWrapper<T, FutureEvent>(typedFutureDecorator.wrap(future),
                        eventFactory, futureCancelEventHandlerFactory, futureResultEventHandlerFactory);
            }
        };
        return callableSubmitWrapper.call(event, task);
    }

    @Override
    public <T> Future<T> submit(final Runnable runnable, final T result) {
        final Runnable decoratedRunnable = runnableDecorator.wrap(runnable);
        @SuppressWarnings("unchecked")
        final FutureDecorator<T> typedFutureDecorator = (FutureDecorator<T>) futureDecorator;
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionEventHandlerFactory);
        final ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(decoratedRunnable);
        final CallTask<Future<T>> task = new CallTask<Future<T>>() {
            @Override
            public Future<T> call() {
                final Future<T> future = orig.submit(instrumentedTask, result);
                return new FutureWrapper<T, FutureEvent>(typedFutureDecorator.wrap(future),
                        eventFactory, futureCancelEventHandlerFactory, futureResultEventHandlerFactory);
            }
        };
        return runnableSubmitWrapper.call(event, task);
    }

    @Override
    public Future<?> submit(final Runnable runnable) {
        final Runnable decoratedRunnable = runnableDecorator.wrap(runnable);
        final RunnableWrapper<?> instrumentedTask = new RunnableWrapper<>(
                decoratedRunnable, eventFactory, runnableExecutionEventHandlerFactory);
        final ThreadPoolEvent event = eventFactory.runnableSubmissionEvent(decoratedRunnable);
        final CallTask<Future<?>> task = new CallTask<Future<?>>() {
            @Override
            public Future<?> call() {
                final Future<?> future = orig.submit(instrumentedTask);
                @SuppressWarnings("unchecked")
                final Future<Object> futureObject = (Future<Object>) future;
                return new FutureWrapper<Object, FutureEvent>(futureDecoratorObject.wrap(futureObject),
                        eventFactory, futureCancelEventHandlerFactory, futureResultEventHandlerFactory);
            }
        };
        return runnableSubmitWrapper.call(event, task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        final int taskCount = tasks.size();
        @SuppressWarnings("unchecked")
        final CallableDecorator<T> typedCallableDecorator = (CallableDecorator<T>) callableDecorator;
        @SuppressWarnings("unchecked")
        final FutureDecorator<T> typedFutureDecorator = (FutureDecorator<T>) futureDecorator;
        final Collection<Callable<T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final Callable<T> decoratedTask = typedCallableDecorator.wrap(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionEventHandlerFactory);
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
                for (Future<T> each: futures) {
                    final Future<T> instrumentedFuture = new FutureWrapper<T, FutureEvent>(
                            typedFutureDecorator.wrap(each), eventFactory,
                            futureCancelEventHandlerFactory, futureResultEventHandlerFactory);
                    futWrappers.add(instrumentedFuture);
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
        @SuppressWarnings("unchecked")
        final FutureDecorator<T> typedFutureDecorator = (FutureDecorator<T>) futureDecorator;
        final Collection<Callable<T>> decoratedTasks = new ArrayList<>(taskCount);
        final List<CallableWrapper<T, ExecutionEvent>> instrumentedTasks = new ArrayList<>(taskCount);
        for (Callable<T> each: tasks) {
            final Callable<T> decoratedTask = typedCallableDecorator.wrap(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionEventHandlerFactory);
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
                for (Future<T> each: futures) {
                    final Future<T> instrumentedFuture = new FutureWrapper<T, FutureEvent>(
                            typedFutureDecorator.wrap(each), eventFactory,
                            futureCancelEventHandlerFactory, futureResultEventHandlerFactory);
                    futWrappers.add(instrumentedFuture);
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
            final Callable<T> decoratedTask = typedCallableDecorator.wrap(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionEventHandlerFactory);
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
            final Callable<T> decoratedTask = typedCallableDecorator.wrap(each);
            final CallableWrapper<T, ExecutionEvent> eachInstrumentedTask = new CallableWrapper<>(
                    decoratedTask, eventFactory, callableExecutionEventHandlerFactory);
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
