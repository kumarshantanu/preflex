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

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface ConcurrentEventFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> {

    public ThreadPoolEvent runnableSubmissionEvent(Runnable command);
    public <V> ThreadPoolEvent callableSubmissionEvent(Callable<V> command);
    public <V> ThreadPoolEvent callableCollectionSubmissionEvent(Collection<? extends Callable<V>> callables);
    public ThreadPoolEvent shutdownEvent();

    public <V> ExecutionEvent callableExecutionEvent(Callable<V> task);
    public ExecutionEvent runnableExecutionEvent(Runnable task);

    public <V> FutureEvent cancellationEvent(Future<V> future);
    public <V> FutureEvent resultFetchEvent(Future<V> future);

}
