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
    [preflex.internal  :as in]
    [preflex.util      :as u])
  (:import
    [java.util.concurrent Callable ExecutorService Future]
    [preflex.instrument EventHandler EventHandlerFactory SharedContext]
    [preflex.instrument.concurrent
     CallableDecorator
     ConcurrentEventFactory
     ConcurrentEventHandlerFactory
     ExecutorServiceWrapper
     RunnableDecorator
     SharedContextCallable
     SharedContextRunnable]))


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


(defmacro with-shared-context
  "Given a symbol (to bind to wrapped context) and preflex.instrument.SharedContext instance, evaluate body of code in
  the binding context."
  [[context holder] & body]
  (in/expected symbol? "a symbol to bind the context to" context)
  `(let [^SharedContext holder# ~holder
         ~context (.getContext holder#)]
     ~@body))


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
    {:callable-submit  (fn [task]  {:event-context :thread-pool   :event-type :callable-submit  :callable task})
     :multiple-submit  (fn [tasks] {:event-context :thread-pool   :event-type :multiple-submit  :multiple tasks})
     :runnable-submit  (fn [task]  {:event-context :thread-pool   :event-type :runnable-submit  :runnable task})
     :shutdown-request (fn []      {:event-context :thread-pool   :event-type :shutdown-request})
     :callable-execute (fn [task]  {:event-context :thread-exec   :event-type :callable-execute :callable task})
     :runnable-execute (fn [task]  {:event-context :thread-exec   :event-type :runnable-execute :runnable task})
     :future-cancel    (fn [fut]   {:event-context :thread-future :event-type :future-cancel    :future   fut})
     :future-result    (fn [fut]   {:event-context :thread-future :event-type :future-result    :future   fut})}))


(def shared-context-callable-decorator
  (reify CallableDecorator
    (wrap [this callable] (SharedContextCallable. callable (volatile! {})))))


(def shared-context-runnable-decorator
  (reify RunnableDecorator
    (wrap [this runnable] (SharedContextRunnable. runnable (volatile! {})))))


(defn shared-context-update-event
  [event event-k f]
  (when (contains? event event-k)
    (with-shared-context [context (get event event-k)]
      (f context))))


(def shared-context-event-handlers
  (let [after-submit   (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [volatile-context]
                             (vswap! volatile-context
                               (fn [{:keys [^long submit-begin-ns] :as context}]
                                 (let [now-ns (u/now-nanos)]
                                   (assoc context
                                     :submit-end-ns      now-ns
                                     :duration-submit-ns (unchecked-subtract now-ns submit-begin-ns))))))))
        before-execute (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [volatile-context]
                             (vswap! volatile-context
                               (fn [{:keys [^long submit-begin-ns ^long submit-end-ns] :as context}]
                                 (let [now-ns (u/now-nanos)]
                                   (assoc context
                                     :execute-begin-ns   now-ns
                                     :duration-queue-ns  (unchecked-subtract now-ns
                                                           ;; submit-end-ns may not be updated (race condition)
                                                           ^long (or submit-end-ns submit-begin-ns)))))))))
        after-execute  (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [volatile-context]
                             (vswap! volatile-context
                               (fn [{:keys [^long submit-begin-ns ^long execute-begin-ns] :as context}]
                                 (let [now-ns (u/now-nanos)]
                                   (assoc context
                                     :execute-end-ns now-ns
                                     :duration-execute-ns  (unchecked-subtract now-ns execute-begin-ns)
                                     :duration-response-ns (unchecked-subtract now-ns submit-begin-ns))))))))
        assoc-context  (fn [event-k context-k event]
                         (shared-context-update-event event event-k
                           (fn [volatile-context]
                             (vswap! volatile-context
                               (fn [context]
                                 (assoc context context-k (u/now-nanos)))))))]
    {:on-callable-submit  {:before (partial assoc-context  :callable :submit-begin-ns)
                           :after  (partial after-submit   :callable)}
     :on-runnable-submit  {:before (partial assoc-context  :runnable :submit-begin-ns)
                           :after  (partial after-submit   :runnable)}
     :on-callable-execute {:before (partial before-execute :callable)
                           :after  (partial after-execute  :callable)}
     :on-runnable-execute {:before (partial before-execute :runnable)
                           :after  (partial after-execute  :runnable)}
     :on-future-cancel    {:before (partial assoc-context  :future   :cancel-begin-ns)
                           :after  (partial assoc-context  :future   :cancel-end-ns)}
     :on-future-result    {:before (partial assoc-context  :future   :result-begin-ns)
                           :after  (partial assoc-context  :future   :result-end-ns)}}))


(defn instrument-thread-pool
  "Given a thread pool, an event generator and optional event handlers instrument the thread pool such that the events
  are raised and handled at the appropriate time. Options are as follows:

  ;; event generator
  :event-generator    instance of preflex.instrument.concurrent.ConcurrentEventFactory

  ;; event handlers
  on-callable-submit  instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-multiple-submit  instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-runnable-submit  instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-shutdown-request instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-callable-execute instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-runnable-execute instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-future-cancel    instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg
  on-future-result    instance of preflex.instrument.EventHandlerFactory or `event-handler-opts->factory` arg

  ;; decorators
  :callable-decorator instance of preflex.instrument.concurrent.CallableDecorator
  :runnable-decorator instance of preflex.instrument.concurrent.RunnableDecorator

  See also:
  event-handler-opts->factory
  default-thread-pool-event-generator"
  [^ExecutorService thread-pool {:keys [;; decorators
                                        callable-decorator
                                        runnable-decorator
                                        ;; event generator
                                        event-generator
                                        ;; event handlers
                                        on-callable-submit
                                        on-multiple-submit
                                        on-runnable-submit
                                        on-shutdown-request
                                        on-callable-execute
                                        on-runnable-execute
                                        on-future-cancel
                                        on-future-result]
                                 :or {;; decorators
                                      callable-decorator CallableDecorator/IDENTITY
                                      runnable-decorator RunnableDecorator/IDENTITY
                                      ;; event generator
                                      event-generator    default-thread-pool-event-generator
                                      ;; event handlers
                                      on-callable-submit  EventHandlerFactory/NOP
                                      on-multiple-submit  EventHandlerFactory/NOP
                                      on-runnable-submit  EventHandlerFactory/NOP
                                      on-shutdown-request EventHandlerFactory/NOP
                                      on-callable-execute EventHandlerFactory/NOP
                                      on-runnable-execute EventHandlerFactory/NOP
                                      on-future-cancel    EventHandlerFactory/NOP
                                      on-future-result    EventHandlerFactory/NOP}
                                 :as opts}]
  (ExecutorServiceWrapper.
    thread-pool
    event-generator
    (let [as-event-handler-factory #(if (instance? EventHandlerFactory %) % (event-handler-opts->factory %))]
      (ConcurrentEventHandlerFactory.
        (as-event-handler-factory on-callable-submit)
        (as-event-handler-factory on-multiple-submit)
        (as-event-handler-factory on-runnable-submit)
        (as-event-handler-factory on-shutdown-request)
        (as-event-handler-factory on-callable-execute)
        (as-event-handler-factory on-runnable-execute)
        (as-event-handler-factory on-future-cancel)
        (as-event-handler-factory on-future-result)))
    ;; decorators
    callable-decorator
    runnable-decorator))
