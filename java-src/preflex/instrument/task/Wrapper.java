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

public interface Wrapper<K> {

    /**
     * A wrapper instance that does nothing apart from invoking the specified task.
     */
    Wrapper<?> IDENTITY = new Wrapper<Object>() {
        @Override
        public void run(Object context, RunTask task) {
            task.run();
        }

        @Override
        public <A extends Throwable> void run(Object context, RunTask1<A> task, Class<A> a) throws A {
            task.run();
        }

        @Override
        public <A extends Throwable, B extends Throwable> void run(Object context, RunTask2<A, B> task, Class<A> a,
                Class<B> b) throws A, B {
            task.run();
        }

        @Override
        public <A extends Throwable, B extends Throwable, C extends Throwable> void run(Object context,
                RunTask3<A, B, C> task, Class<A> a, Class<B> b, Class<C> c) throws A, B, C {
            task.run();
        }

        @Override
        public <A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> void run(
                Object context, RunTask4<A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> d)
                        throws A, B, C, D {
            task.run();
        }

        @Override
        public <T> T call(Object context, CallTask<T> task) {
            return task.call();
        }

        @Override
        public <T, A extends Throwable> T call(Object context, CallTask1<T, A> task, Class<A> clazz) throws A {
            return task.call();
        }

        @Override
        public <T, A extends Throwable, B extends Throwable> T call(Object context, CallTask2<T, A, B> task, Class<A> a,
                Class<B> b) throws A, B {
            return task.call();
        }

        @Override
        public <T, A extends Throwable, B extends Throwable, C extends Throwable> T call(Object context,
                CallTask3<T, A, B, C> task, Class<A> a, Class<B> b, Class<C> c) throws A, B, C {
            return task.call();
        }

        @Override
        public <T, A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> T call(
                Object context, CallTask4<T, A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> d)
                        throws A, B, C, D {
            return task.call();
        }
    };

    // methods without a return type

    void run(K context, RunTask task);

    <A extends Throwable> void run(K context, RunTask1<A> task, Class<A> a) throws A;

    <A extends Throwable, B extends Throwable> void run(K context, RunTask2<A, B> task, Class<A> a, Class<B> b)
            throws A, B;

    <A extends Throwable, B extends Throwable, C extends Throwable> void run(K context, RunTask3<A, B, C> task,
            Class<A> a, Class<B> b, Class<C> c) throws A, B, C;

    <A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> void run(K context,
            RunTask4<A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> da) throws A, B, C, D;

    // methods with a return type

    <T> T call(K context, CallTask<T> task);

    <T, A extends Throwable> T call(K context, CallTask1<T, A> task, Class<A> clazz) throws A;

    <T, A extends Throwable, B extends Throwable> T call(K context, CallTask2<T, A, B> task, Class<A> a, Class<B> b)
            throws A, B;

    <T, A extends Throwable, B extends Throwable, C extends Throwable> T call(K context, CallTask3<T, A, B, C> task,
            Class<A> a, Class<B> b, Class<C> c) throws A, B, C;

    <T, A extends Throwable, B extends Throwable, C extends Throwable, D extends Throwable> T call(K context,
            CallTask4<T, A, B, C, D> task, Class<A> a, Class<B> b, Class<C> c, Class<D> d) throws A, B, C, D;

}
