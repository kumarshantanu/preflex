;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.instrument
  (:require
    [preflex.invokable :as invokable]
    [preflex.internal  :as in])
  (:import
    [java.util.concurrent Callable ExecutorService Future]
    [preflex.instrument EventHandler EventHandlerFactory SharedContext]
    [preflex.instrument.concurrent
     CallableDecorator
     CallableWrapper
     ConcurrentEventFactory
     DefaultDecoratedCallable
     DefaultDecoratedFuture
     DefaultDecoratedRunnable
     ExecutorServiceWrapper
     FutureDecorator
     FutureWrapper
     RunnableDecorator
     RunnableWrapper]))


(defn make-event-handler
  "Given an instrumentation event create an event handler (preflex.instrument.EventHandler instance) using optional
  handler fns (falling back to no-op handling) for different stages as follows:

  :before    fn/1, accepts event
  :on-return fn/1, accepts event
  :on-result fn/2, accepts event and result
  :on-throw  fn/2, accepts event and exception
  :after     fn/1, accepts event"
  [event {:keys [before
                 on-return
                 on-result
                 on-throw
                 after]
          :or {before    in/nop
               on-return in/nop
               on-result in/nop
               on-throw  in/nop
               after     in/nop}
          :as opts}]
  (reify EventHandler
    (before   [this]           (before    event))
    (onReturn [this]           (on-return event))
    (onResult [this result]    (on-result event result))
    (onThrow  [this exception] (on-throw  event exception))
    (after    [this]           (after     event))))


(defn event-handler-opts->factory
  "Create an event-handler factory (preflex.instrument.EventHandlerFactory instance) from optional event handler fns.
  See `preflex.instrument/make-event-handler` for options."
  [opts]
  (reify EventHandlerFactory
    (createHandler [this event] (make-event-handler event opts))))


;; ---------- thread pool instrumentation ----------


(defn make-thread-pool-event-generator
  "Create a thread-pool event generator (preflex.instrument.concurrent.ConcurrentEventFactory instance) using optional
  event generators (falling back to generating no-op events) as follows:

  :runnable-submit  fn/1, accepts java.lang.Runnable
  :callable-submit  fn/1, accepts java.util.concurrent.Callable
  :multiple-submit  fn/1, accepts java.util.Collection<java.util.concurrent.Callable>
  :shutdown-request fn/0
  :runnable-execute fn/1, accepts java.lang.Runnable
  :callable-execute fn/1, accepts java.util.concurrent.Callable
  :future-cancel    fn/1, accepts java.util.concurrent.Future
  :future-result    fn/1, accepts java.util.concurrent.Future"
  [{:keys [runnable-submit
           callable-submit
           multiple-submit
           shutdown-request
           runnable-execute
           callable-execute
           future-cancel
           future-result]
    :or {runnable-submit  in/nop
         callable-submit  in/nop
         multiple-submit  in/nop
         shutdown-request in/nop
         runnable-execute in/nop
         callable-execute in/nop
         future-cancel    in/nop
         future-result    in/nop}
    :as opts}]
  (reify ConcurrentEventFactory
    ;; thread-pool events
    (runnableSubmissionEvent           [this task]  (runnable-submit task))
    (callableSubmissionEvent           [this task]  (callable-submit task))
    (callableCollectionSubmissionEvent [this tasks] (multiple-submit tasks))
    (shutdownEvent                     [this]       (shutdown-request))
    ;; execution events
    (runnableExecutionEvent            [this task]  (runnable-execute task))
    (callableExecutionEvent            [this task]  (callable-execute task))
    ;; future events
    (cancellationEvent                 [this fut]   (future-cancel fut))
    (resultFetchEvent                  [this fut]   (future-result fut))))


(def default-thread-pool-event-generator
  "The default thread-pool event generator."
  (make-thread-pool-event-generator
    {:runnable-submit  (fn [task]  {:event-context :thread-pool   :event-type :runnable-submit  :runnable task})
     :callable-submit  (fn [task]  {:event-context :thread-pool   :event-type :callable-submit  :callable task})
     :multiple-submit  (fn [tasks] {:event-context :thread-pool   :event-type :multiple-submit  :multiple tasks})
     :shutdown-request (fn []      {:event-context :thread-pool   :event-type :shutdown-request})
     :runnable-execute (fn [task]  {:event-context :thread-exec   :event-type :runnable-execute :runnable task})
     :callable-execute (fn [task]  {:event-context :thread-exec   :event-type :callable-execute :callable task})
     :future-cancel    (fn [fut]   {:event-context :thread-future :event-type :future-cancel    :future   fut})
     :future-result    (fn [fut]   {:event-context :thread-future :event-type :future-result    :future   fut})}))


(def default-callable-decorator
  (reify CallableDecorator
    (wrap [this callable] (DefaultDecoratedCallable. callable (volatile! {})))))


(def default-runnable-decorator
  (reify RunnableDecorator
    (wrap [this runnable] (DefaultDecoratedRunnable. runnable (volatile! {})))))

(def default-future-decorator
  (reify FutureDecorator
    (wrap [this future-obj] (DefaultDecoratedFuture. future-obj (volatile! {})))))


(defn instrument-thread-pool
  "Given a thread pool, an event generator and optional event handlers instrument the thread pool such that the events
  are raised and handled at the appropriate time. Options are as follows:

  :event-generator              instance of preflex.instrument.concurrent.ConcurrentEventFactory
  :runnable-decorator           nil or instance of preflex.instrument.concurrent.RunnableDecorator
  :callable-decorator           nil or instance of preflex.instrument.concurrent.CallableDecorator
  :future-decorator             nil or instance of preflex.instrument.concurrent.FutureDecorator
  :pool-event-handler-factory   event handler for pool events, instance of preflex.instrument.EventHandlerFactory
  :exec-event-handler-factory   event handler for exec events, instance of preflex.instrument.EventHandlerFactory
  :future-event-handler-factory event handler for future events, instance of preflex.instrument.EventHandlerFactory

  See also:
  event-handler-opts->factory
  default-thread-pool-event-generator"
  [^ExecutorService thread-pool {:keys [;; decorators
                                        runnable-decorator
                                        callable-decorator
                                        future-decorator
                                        ;; event generator
                                        event-generator
                                        ;; event handlers
                                        pool-event-handler-factory
                                        exec-event-handler-factory
                                        future-event-handler-factory]
                                 :or {;; decorators
                                      runnable-decorator default-runnable-decorator ; RunnableDecorator/IDENTITY
                                      callable-decorator default-callable-decorator ; CallableDecorator/IDENTITY
                                      future-decorator   default-future-decorator ; FutureDecorator/IDENTITY
                                      ;; event generator
                                      event-generator    default-thread-pool-event-generator
                                      ;; event handlers
                                      pool-event-handler-factory   EventHandlerFactory/NOP
                                      exec-event-handler-factory   EventHandlerFactory/NOP
                                      future-event-handler-factory EventHandlerFactory/NOP}
                                 :as opts}]
  (ExecutorServiceWrapper. thread-pool event-generator
    ;; event-handler factories
    pool-event-handler-factory
    exec-event-handler-factory
    future-event-handler-factory
    ;; decorators
    runnable-decorator
    callable-decorator
    future-decorator))
