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
    [java.util UUID]
    [java.util.concurrent Callable ExecutorService]
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


(defn shared-context-update-event
  "Given event as an associate structure and an event-key, update the shared context contained in the event attribute
  using the arity-1 update fn argument."
  [event event-k f]
  (when (contains? event event-k)
    (with-shared-context [context (get event event-k)]
      (f context))))


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


(defn make-shared-context-callable-decorator
  "Given invoker `(fn [f context-atom]) -> any`, return a preflex.instrument.concurrent.CallableDecorator instance
  that initializes shared context with a mutable seed and calls the invoker, `f` being callable-as-no-arg-fn."
  [invoker]
  (reify CallableDecorator
    (wrapCallable [this callable] (let [context-atom (atom {})
                                        wrapped-callable (reify Callable
                                                           (call [this] (invoker #(.call callable) context-atom)))]
                                    (SharedContextCallable. wrapped-callable context-atom)))))


(defn make-shared-context-runnable-decorator
  "Given invoker `(fn [f context-atom])`, return a preflex.instrument.concurrent.RunnableDecorator instance that
  initializes shared context with a mutable seed and calls the invoker, `f` being runnable-as-no-arg-fn."
  [invoker]
  (reify RunnableDecorator
    (wrapRunnable [this runnable] (let [context-atom (atom {})
                                        wrapped-runnable (reify Runnable
                                                           (run [this] (invoker #(.run runnable) context-atom)))]
                                    (SharedContextRunnable. wrapped-runnable context-atom)))))


(def default-shared-context-callable-decorator
  "A preflex.instrument.concurrent.CallableDecorator instance that initializes shared context with (atom {})."
  (reify CallableDecorator
    (wrapCallable [this callable] (SharedContextCallable. callable (atom {})))))


(def default-shared-context-runnable-decorator
  "A preflex.instrument.concurrent.RunnableDecorator instance that initializes shared context with (atom {})."
  (reify RunnableDecorator
    (wrapRunnable [this runnable] (SharedContextRunnable. runnable (atom {})))))


(defn make-shared-context-thread-pool-event-handlers
  "Given keyword arguments in a map, return thread-pool instrumentation event handlers."
  [{:keys [now-fn
           k-submit-begin
           k-submit-end
           k-duration-submit
           k-execute-begin
           k-execute-end
           k-duration-queue
           k-duration-execute
           k-duration-response
           k-future-cancel-begin
           k-future-cancel-end
           k-future-result-begin
           k-future-result-end]}]
  (let [after-submit   (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [context-atom]
                             (swap! context-atom
                               (fn [{^long submit-begin-ts k-submit-begin
                                     :as context}]
                                 (let [^long now-ts (now-fn)
                                       duration-submit (unchecked-subtract now-ts submit-begin-ts)]
                                   (assoc context
                                     k-submit-end      now-ts
                                     k-duration-submit duration-submit)))))))
        before-execute (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [context-atom]
                             (swap! context-atom
                               (fn [{^long submit-begin-ts k-submit-begin
                                     :as context}]
                                 (let [^long now-ts (now-fn)
                                       duration-queue (unchecked-subtract now-ts submit-begin-ts)]
                                   (assoc context
                                     k-execute-begin   now-ts
                                     k-duration-queue  duration-queue)))))))
        after-execute  (fn [event-k event]
                         (shared-context-update-event event event-k
                           (fn [context-atom]
                             (swap! context-atom
                               (fn [{^long submit-begin-ts  k-submit-begin
                                     ^long execute-begin-ts k-execute-begin
                                     :as context}]
                                 (let [^long now-ts (now-fn)
                                       duration-execute  (unchecked-subtract now-ts execute-begin-ts)
                                       duration-response (unchecked-subtract now-ts submit-begin-ts)]
                                   (assoc context
                                     k-execute-end now-ts
                                     k-duration-execute  duration-execute
                                     k-duration-response duration-response)))))))
        assoc-context  (fn [event-k context-k event]
                         (shared-context-update-event event event-k
                           (fn [context-atom]
                             (swap! context-atom
                               (fn [context]
                                 (assoc context context-k (now-fn)))))))]
    {:on-callable-submit  {:before (partial assoc-context  :callable k-submit-begin)
                           :after  (partial after-submit   :callable)}
     :on-runnable-submit  {:before (partial assoc-context  :runnable k-submit-begin)
                           :after  (partial after-submit   :runnable)}
     :on-callable-execute {:before (partial before-execute :callable)
                           :after  (partial after-execute  :callable)}
     :on-runnable-execute {:before (partial before-execute :runnable)
                           :after  (partial after-execute  :runnable)}
     :on-future-cancel    {:before (partial assoc-context  :future   k-future-cancel-begin)
                           :after  (partial assoc-context  :future   k-future-cancel-end)}
     :on-future-result    {:before (partial assoc-context  :future   k-future-result-begin)
                           :after  (partial assoc-context  :future   k-future-result-end)}}))


(def shared-context-thread-pool-event-handlers-nanos  (make-shared-context-thread-pool-event-handlers
                                                        {:now-fn                u/now-nanos
                                                         :k-submit-begin        :submit-begin-ns
                                                         :k-submit-end          :submit-end-ns
                                                         :k-duration-submit     :duration-submit-ns
                                                         :k-execute-begin       :execute-begin-ns
                                                         :k-execute-end         :execute-end-ns
                                                         :k-duration-queue      :duration-queue-ns
                                                         :k-duration-execute    :duration-execute-ns
                                                         :k-duration-response   :duration-response-ns
                                                         :k-future-cancel-begin :cancel-begin-ns
                                                         :k-future-cancel-end   :cancel-end-ns
                                                         :k-future-result-begin :result-begin-ns
                                                         :k-future-result-end   :result-end-ns}))


(def shared-context-thread-pool-event-handlers-millis (make-shared-context-thread-pool-event-handlers
                                                        {:now-fn                u/now-millis
                                                         :k-submit-begin        :submit-begin-ms
                                                         :k-submit-end          :submit-end-ms
                                                         :k-duration-submit     :duration-submit-ms
                                                         :k-execute-begin       :execute-begin-ms
                                                         :k-execute-end         :execute-end-ms
                                                         :k-duration-queue      :duration-queue-ms
                                                         :k-duration-execute    :duration-execute-ms
                                                         :k-duration-response   :duration-response-ms
                                                         :k-future-cancel-begin :cancel-begin-ms
                                                         :k-future-cancel-end   :cancel-end-ms
                                                         :k-future-result-begin :result-begin-ms
                                                         :k-future-result-end   :result-end-ms}))


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
