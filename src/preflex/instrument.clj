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
    [preflex.task      :as task]
    [preflex.util      :as u])
  (:import
    [java.util UUID]
    [java.util.concurrent Callable ExecutorService]
    [preflex.instrument EventHandler EventHandlerFactory SharedContext]
    [preflex.instrument.concurrent
     CallableDecorator
     ConcurrentEventFactory
     ConcurrentTaskWrapper
     ExecutorServiceWrapper
     RunnableDecorator
     SharedContextCallable
     SharedContextRunnable]
    [preflex.instrument.task
     Wrapper]))


(defn make-event-handler
  "Given an instrumentation event create an event handler (preflex.instrument.EventHandler instance) using optional
  handler fns (falling back to no-op handling) for different stages as follows:

  :before    (fn [event])
  :on-return (fn [event])
  :on-result (fn [event result])
  :on-throw  (fn [event exception])
  :after     (fn [event])"
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

  :runnable-submit  (fn [java.lang.Runnable])
  :callable-submit  (fn [java.util.concurrent.Callable])
  :multiple-submit  (fn [java.util.Collection<java.util.concurrent.Callable>])
  :shutdown-request (fn [])
  :runnable-execute (fn [java.lang.Runnable])
  :callable-execute (fn [java.util.concurrent.Callable])
  :future-cancel    (fn [java.util.concurrent.Future])
  :future-result    (fn [java.util.concurrent.Future])"
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


(defn make-shared-context-thread-pool-task-wrappers
  "Given keyword arguments in a map, return thread-pool instrumentation task wrapper fns."
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
    {:callable-submit-wrapper  (fn [event f] (try       (assoc-context  :callable k-submit-begin event)        (f)
                                               (finally (after-submit   :callable event))))
     :runnable-submit-wrapper  (fn [event f] (try       (assoc-context  :runnable k-submit-begin event)        (f)
                                       (finally (after-submit   :runnable event))))
     :callable-execute-wrapper (fn [event f] (try       (before-execute :callable event)                       (f)
                                       (finally (after-execute  :callable event))))
     :runnable-execute-wrapper (fn [event f] (try       (before-execute :runnable event)                       (f)
                                       (finally (after-execute  :runnable event))))
     :future-cancel-wrapper    (fn [event f] (try       (assoc-context  :future   k-future-cancel-begin event) (f)
                                       (finally (assoc-context  :future   k-future-cancel-end   event))))
     :future-result-wrapper    (fn [event f] (try       (assoc-context  :future   k-future-result-begin event) (f)
                                       (finally (assoc-context  :future   k-future-result-end   event))))}))


(def shared-context-thread-pool-task-wrappers-nanos  (make-shared-context-thread-pool-task-wrappers
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


(def shared-context-thread-pool-task-wrappers-millis (make-shared-context-thread-pool-task-wrappers
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

  ;; task wrappers
  :callable-submit-wrapper  instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :multiple-submit-wrapper  instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :runnable-submit-wrapper  instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :shutdown-request-wrapper instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :callable-execute-wrapper instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :runnable-execute-wrapper instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :future-cancel-wrapper    instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`
  :future-result-wrapper    instance of preflex.instrument.task.Wrapper or argument to `preflex.task/make-wrapper`

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
                                        ;; task wrappers
                                        callable-submit-wrapper
                                        multiple-submit-wrapper
                                        runnable-submit-wrapper
                                        shutdown-request-wrapper
                                        callable-execute-wrapper
                                        runnable-execute-wrapper
                                        future-cancel-wrapper
                                        future-result-wrapper]
                                 :or {;; decorators
                                      callable-decorator CallableDecorator/IDENTITY
                                      runnable-decorator RunnableDecorator/IDENTITY
                                      ;; event generator
                                      event-generator    default-thread-pool-event-generator
                                      ;; task wrappers
                                      callable-submit-wrapper  Wrapper/IDENTITY
                                      multiple-submit-wrapper  Wrapper/IDENTITY
                                      runnable-submit-wrapper  Wrapper/IDENTITY
                                      shutdown-request-wrapper Wrapper/IDENTITY
                                      callable-execute-wrapper Wrapper/IDENTITY
                                      runnable-execute-wrapper Wrapper/IDENTITY
                                      future-cancel-wrapper    Wrapper/IDENTITY
                                      future-result-wrapper    Wrapper/IDENTITY
                                      }
                                 :as opts}]
  (ExecutorServiceWrapper.
    thread-pool
    event-generator
    (let [as-task-wrapper (fn ^Wrapper [x] (if (fn? x) (task/make-wrapper x) x))]
      (ConcurrentTaskWrapper.
        (as-task-wrapper callable-submit-wrapper)
        (as-task-wrapper multiple-submit-wrapper)
        (as-task-wrapper runnable-submit-wrapper)
        (as-task-wrapper shutdown-request-wrapper)
        (as-task-wrapper callable-execute-wrapper)
        (as-task-wrapper runnable-execute-wrapper)
        (as-task-wrapper future-cancel-wrapper)
        (as-task-wrapper future-result-wrapper)))
    ;; decorators
    callable-decorator
    runnable-decorator))
