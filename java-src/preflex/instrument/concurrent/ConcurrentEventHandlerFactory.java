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

import preflex.instrument.EventHandlerFactory;

public class ConcurrentEventHandlerFactory<ThreadPoolEvent, FutureEvent, ExecutionEvent> {

    public final EventHandlerFactory<ThreadPoolEvent> callableSubmitHandlerFactory;
    public final EventHandlerFactory<ThreadPoolEvent> multipleSubmitHandlerFactory;
    public final EventHandlerFactory<ThreadPoolEvent> runnableSubmitHandlerFactory;
    public final EventHandlerFactory<ThreadPoolEvent> shutdownRequestHandlerFactory;

    public final EventHandlerFactory<ExecutionEvent>  callableExecuteHandlerFactory;
    public final EventHandlerFactory<ExecutionEvent>  runnableExecuteHandlerFactory;

    public final EventHandlerFactory<FutureEvent>     futureCancelHandlerFactory;
    public final EventHandlerFactory<FutureEvent>     futureResultHandlerFactory;

    public ConcurrentEventHandlerFactory(
            EventHandlerFactory<ThreadPoolEvent> callableSubmitHandlerFactory,
            EventHandlerFactory<ThreadPoolEvent> multipleSubmitHandlerFactory,
            EventHandlerFactory<ThreadPoolEvent> runnableSubmitHandlerFactory,
            EventHandlerFactory<ThreadPoolEvent> shutdownRequestHandlerFactory,
            EventHandlerFactory<ExecutionEvent>  callableExecuteHandlerFactory,
            EventHandlerFactory<ExecutionEvent>  runnableExecuteHandlerFactory,
            EventHandlerFactory<FutureEvent>     futureCancelHandlerFactory,
            EventHandlerFactory<FutureEvent>     futureResultHandlerFactory) {
        this.callableSubmitHandlerFactory  = callableSubmitHandlerFactory;
        this.multipleSubmitHandlerFactory  = multipleSubmitHandlerFactory;
        this.runnableSubmitHandlerFactory  = runnableSubmitHandlerFactory;
        this.shutdownRequestHandlerFactory = shutdownRequestHandlerFactory;
        this.callableExecuteHandlerFactory = callableExecuteHandlerFactory;
        this.runnableExecuteHandlerFactory = runnableExecuteHandlerFactory;
        this.futureCancelHandlerFactory    = futureCancelHandlerFactory;
        this.futureResultHandlerFactory    = futureResultHandlerFactory;
    }

}
