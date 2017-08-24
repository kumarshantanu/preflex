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

import preflex.instrument.task.Wrapper;

public class ConcurrentTaskWrapper<ThreadPoolEvent, FutureEvent, ExecutionEvent> {

    public final Wrapper<ThreadPoolEvent> callableSubmitWrapper;
    public final Wrapper<ThreadPoolEvent> multipleSubmitWrapper;
    public final Wrapper<ThreadPoolEvent> runnableSubmitWrapper;
    public final Wrapper<ThreadPoolEvent> shutdownRequestWrapper;

    public final Wrapper<ExecutionEvent>  callableExecuteWrapper;
    public final Wrapper<ExecutionEvent>  runnableExecuteWrapper;

    public final Wrapper<FutureEvent>     futureCancelWrapper;
    public final Wrapper<FutureEvent>     futureResultWrapper;

    public ConcurrentTaskWrapper(
            Wrapper<ThreadPoolEvent> callableSubmitWrapper,
            Wrapper<ThreadPoolEvent> multipleSubmitWrapper,
            Wrapper<ThreadPoolEvent> runnableSubmitWrapper,
            Wrapper<ThreadPoolEvent> shutdownRequestWrapper,
            Wrapper<ExecutionEvent>  callableExecuteWrapper,
            Wrapper<ExecutionEvent>  runnableExecuteWrapper,
            Wrapper<FutureEvent>     futureCancelWrapper,
            Wrapper<FutureEvent>     futureResultWrapper) {
        this.callableSubmitWrapper  = callableSubmitWrapper;
        this.multipleSubmitWrapper  = multipleSubmitWrapper;
        this.runnableSubmitWrapper  = runnableSubmitWrapper;
        this.shutdownRequestWrapper = shutdownRequestWrapper;
        this.callableExecuteWrapper = callableExecuteWrapper;
        this.runnableExecuteWrapper = runnableExecuteWrapper;
        this.futureCancelWrapper    = futureCancelWrapper;
        this.futureResultWrapper    = futureResultWrapper;
    }

}
