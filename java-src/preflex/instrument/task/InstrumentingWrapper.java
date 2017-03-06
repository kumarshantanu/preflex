/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument.task;

import preflex.instrument.EventHandler;
import preflex.instrument.EventHandlerFactory;

public class InstrumentingWrapper<V> implements Wrapper<V> {

    private final EventHandlerFactory<V> eventHandlerFactory;

    public InstrumentingWrapper(EventHandlerFactory<V> eventHandlerFactory) {
        this.eventHandlerFactory = eventHandlerFactory;
    }

    @Override
    public <T, A extends Throwable> T call(V event, CallTask1<T, A> task, Class<A> clazz) throws A {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            T result = task.call();
            eh.onReturn(result);
            return result;
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <T, A extends Throwable, B extends Throwable> T call(V event, CallTask2<T, A, B> task, Class<A> a,
            Class<B> b) throws A, B {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            T result = task.call();
            eh.onReturn(result);
            return result;
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <T, A extends Throwable, B extends Throwable, C extends Throwable> T call(V event,
            CallTask3<T, A, B, C> task, Class<A> a, Class<B> b, Class<C> c) throws A, B, C {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            T result = task.call();
            eh.onReturn(result);
            return result;
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <T, A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> T call(V event,
            CallTask4<T, A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> d) throws A, B, C, D {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            T result = task.call();
            eh.onReturn(result);
            return result;
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <T> T call(V event, CallTask<T> task) {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            T result = task.call();
            eh.onReturn(result);
            return result;
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public void run(V event, RunTask task) {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            task.run();
            eh.onReturn();
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <A extends Throwable> void run(V event, RunTask1<A> task, Class<A> a) throws A {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            task.run();
            eh.onReturn();
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <A extends Throwable, B extends Throwable> void run(V event, RunTask2<A, B> task, Class<A> a, Class<B> b)
            throws A, B {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            task.run();
            eh.onReturn();
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <A extends Throwable, B extends Throwable, C extends Throwable> void run(V event, RunTask3<A, B, C> task,
            Class<A> a, Class<B> b, Class<C> c) throws A, B, C {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            task.run();
            eh.onReturn();
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

    @Override
    public <A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> void run(V event,
            RunTask4<A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> da) throws A, B, C, D {
        EventHandler eh = eventHandlerFactory.createHandler(event);
        eh.before();
        try {
            task.run();
            eh.onReturn();
        } catch (Exception e) {
            eh.onThrow(e);
            throw e;
        } finally {
            eh.after();
        }
    }

}
