;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.core
  "Resilience abstractions with backpressure:
  * Bounded thread pool - (compared to unbounded thread pool) helps keep computation and memory consumption in check
  * Circuit breaker     - cuts off execution when a resource is unavailable, and resumes when it is available again
  * Semaphore           - limits total number of clients competing for resources
  * Fallback            - When primary computation fails, fall back on standby"
  (:require
    [preflex.impl      :as im]
    [preflex.internal  :as in]
    [preflex.invokable :as iv]
    [preflex.error     :as e]
    [preflex.metrics   :as m]
    [preflex.type      :as t]
    [preflex.util      :as u])
  (:import
    [java.util.concurrent
     ArrayBlockingQueue ExecutorService Future Semaphore ThreadPoolExecutor TimeUnit
     ExecutionException RejectedExecutionException TimeoutException]
    [preflex.impl RetryState]))


;; ----- bounded thread pool -----


(defn make-bounded-thread-pool
  "Given max thread-count, work queue-size and options, create and return a bounded thread pool.
  Options:
    :thread-pool-name     (any type) thread-pool name, coerced as string
    :keep-alive-duration  (Duration) timeout for idle threads after which they may be terminated
    :core-thread-count    (int)      core thread count
    :core-thread-timeout? (boolean)  whether idle core threads should be terminated after timeout"
  ([^long max-thread-count ^long queue-capacity {:keys [thread-pool-name
                                                        keep-alive-duration
                                                        core-thread-count
                                                        core-thread-timeout?]
                                                 :or {thread-pool-name     (gensym "bounded-thread-pool-")
                                                      keep-alive-duration  [10000 :millis]
                                                      core-thread-count    max-thread-count
                                                      core-thread-timeout? true}
                                                 :as options}]
    (let [thread-pool (doto (->> (ArrayBlockingQueue. (int queue-capacity))
                              (ThreadPoolExecutor. core-thread-count max-thread-count
                                (t/duration-time keep-alive-duration)
                                (t/duration-unit keep-alive-duration)))
                        (.allowCoreThreadTimeOut (boolean core-thread-timeout?))
                        (.prestartAllCoreThreads))]
      (im/->BoundedThreadPool (in/as-str thread-pool-name) thread-pool (int queue-capacity))))
  ([^long max-thread-count ^long queue-capacity]
    (make-bounded-thread-pool max-thread-count queue-capacity {})))


(defn future-call-via
  "Same as `clojure.core/future-call`, but for a specified thread pool with instrumentation.
  Options:
    :context-maker   fn/1 - creates context to be passed as first arg to other listeners
    :on-task-submit  fn/1 - called when task submission succeeds on the thread pool
    :on-task-reject  fn/2 - called when task submission is rejected on the thread pool
    :on-task-error   fn/2 - called when the future object cannot be derefed successfully
    :on-task-timeout fn/2 - called when the future object cannot be derefed in specified time"
  ([^ExecutorService thread-pool {:keys [context-maker
                                         on-task-submit
                                         on-task-reject
                                         on-task-error
                                         on-task-timeout]
                                  :or {context-maker   im/make-context
                                       on-task-submit  in/nop
                                       on-task-reject  (fn [_ _] (e/thread-pool-rejected))
                                       on-task-error   (fn [_ e] (e/exception-occurred e))
                                       on-task-timeout (fn [_ _] (e/operation-timed-out))}}
    f]
    (let [ctx (context-maker thread-pool)]
      (try
        (let [^Future fut (.submit thread-pool ^Callable (fn [] (t/apply-noarg f)))]
          (on-task-submit ctx)
          (reify
            clojure.lang.IDeref
            (deref [_] (try (in/deref-future fut)
                         (catch InterruptedException e
                           (.interrupt ^Thread (Thread/currentThread))
                           (on-task-error ctx e))
                         (catch ExecutionException e
                           (on-task-error ctx (.getCause e)))))
            clojure.lang.IBlockingDeref
            (deref [_ timeout-ms timeout-val] (try (in/deref-future fut timeout-ms timeout-val)
                                                (catch InterruptedException e
                                                  (.interrupt ^Thread (Thread/currentThread))
                                                  (on-task-error ctx e))
                                                (catch ExecutionException e
                                                  (on-task-error ctx (.getCause e)))
                                                (catch TimeoutException e
                                                  (on-task-timeout ctx e))))
            clojure.lang.IPending
            (isRealized [_] (.isDone fut))
            java.util.concurrent.Future
            (get [_] (try (.get fut)
                       (catch InterruptedException e
                         (.interrupt ^Thread (Thread/currentThread))
                         (on-task-error ctx e))
                       (catch ExecutionException e
                         (on-task-error ctx (.getCause e)))))
            (get [_ timeout unit] (try (.get fut timeout unit)
                                    (catch InterruptedException e
                                      (.interrupt ^Thread (Thread/currentThread))
                                      (on-task-error ctx e))
                                    (catch ExecutionException e
                                      (on-task-error ctx (.getCause e)))
                                    (catch TimeoutException e
                                      (on-task-timeout ctx e))))
            (isCancelled [_] (.isCancelled fut))
            (isDone [_] (.isDone fut))
            (cancel [_ interrupt?] (.cancel fut interrupt?))))
        (catch RejectedExecutionException e
          (on-task-reject ctx e)))))
  ([^ExecutorService thread-pool f]
    (future-call-via thread-pool {} f)))


(defn via-thread-pool
  "Execute given task (no-arg fn) asynchronously on specified thread pool and return result.
  Options:
    :context-maker   fn/1  - creates context to be passed as first arg to other listeners
    :on-task-submit  fn/1  - called when task submission succeeds on the thread pool
    :on-task-reject  fn/2  - called when task submission is rejected on the thread pool
    :on-task-error   fn/2  - called when the future object cannot be derefed successfully
    :on-task-timeout fn/2  - called when the future object cannot be derefed in specified time
    :task-timeout    proto - timeout duration as preflex.type/IDuration instance e.g. [1000 :millis]"
  ([^ExecutorService thread-pool {:keys [context-maker
                                         on-task-submit
                                         on-task-reject
                                         on-task-error
                                         on-task-timeout
                                         task-timeout]
                                  :or {context-maker   im/make-context
                                       on-task-submit  in/nop
                                       on-task-reject  (fn [_ _] (e/thread-pool-rejected))
                                       on-task-error   (fn [_ e] (e/exception-occurred e))
                                       on-task-timeout (fn [_ _] (e/operation-timed-out))}
                                  :as options}
    f]
    (let [ctx (context-maker thread-pool)]
      (try
        (let [^Future future (.submit thread-pool ^Callable (fn [] (t/apply-noarg f)))]
          (on-task-submit ctx)
          (try
            (if task-timeout
              (try (.get future (t/duration-time task-timeout) (t/duration-unit task-timeout))
                (catch TimeoutException e
                  (on-task-timeout ctx e)))
              (.get future))
            (catch InterruptedException e
              (.interrupt ^Thread (Thread/currentThread))
              (on-task-error ctx e))
            (catch ExecutionException e
              (on-task-error ctx (.getCause e)))))
        (catch RejectedExecutionException e
          (on-task-reject ctx e)))))
  ([thread-pool f]
    (via-thread-pool thread-pool {} f)))


;; ----- semaphore -----


(defn make-counting-semaphore
  "Given max permits count, create and return a counting semaphore.
  Options:
    :semaphore-name  (any type) semaphore name, coerced as string
    :semaphore-fair? (boolean)  whether semaphore should use fair acquisition"
  ([^long max-permits {:keys [semaphore-name
                              semaphore-fair?]
                       :or {semaphore-name (gensym "counting-semaphore-")
                            semaphore-fair? false}}]
    (let [^Semaphore semaphore (Semaphore. (int max-permits) (boolean semaphore-fair?))]
      (im/->CountingSemaphore (in/as-str semaphore-name) semaphore false (int max-permits))))
  ([^long max-permits]
    (make-counting-semaphore max-permits {})))


(defn via-semaphore
  "Execute given task (no-arg fn) using specified semaphore. Acquire a permit and execute task before finally releasing
  the permit. Handle events on-acquired, on-released, on-rejected using optional handlers. When no permit is available,
  throw appropriate exception by default.
  Options:
    :context-maker        fn/1 - creates context to be passed as first arg to other listeners
    :on-semaphore-acquire fn/1 - accepts context, does nothing by default
    :on-semaphore-release fn/1 - accepts context, does nothing by default
    :on-semaphore-reject  fn/1 - accepts context, does nothing by default"
  ([semaphore {:keys [context-maker
                      on-semaphore-acquire
                      on-semaphore-release
                      on-semaphore-reject]
               :or {context-maker        im/make-context
                    on-semaphore-acquire in/nop
                    on-semaphore-release in/nop
                    on-semaphore-reject  (fn [_] (e/semaphore-rejected))}
               :as options}
    f]
    (let [ctx (context-maker semaphore)]
      (if (t/acquire-permit! semaphore)
        (try
          (on-semaphore-acquire ctx)
          (t/apply-noarg f)
          (finally
            (t/release-permit! semaphore)
            (on-semaphore-release ctx)))
        (on-semaphore-reject ctx))))
  ([semaphore f]
    (via-semaphore semaphore {} f)))


;; ----- circuit breaker -----


(defn make-serial-fault-detector
  "Create a protocols-instance that detects faults based on threshold specified as X consecutive errors."
  [^long connected-until-errcount]
  (let [fault-counter (m/make-integer-counter :count)]
    (reify
      t/IMetricsRecorder   (record! [_] (throw (IllegalArgumentException. "This should never be called")))
                           (record! [_ status?] (if status?
                                                  (t/reinit! fault-counter)  ; success implies fault-counter reset
                                                  (t/record! fault-counter)))
      t/IReinitializable   (reinit! [_] (t/reinit! fault-counter))
      clojure.lang.Counted (count   [_] (count fault-counter))
      clojure.lang.IDeref  (deref   [_] (deref fault-counter))
      t/IFaultDetector     (fault?  [_] (>= (count fault-counter) connected-until-errcount)))))


(defn make-discrete-fault-detector
  "Create a fault detector based on threshold specified as connected-until-errcount errors in
  connected-until-duration discrete milliseconds. This follows the X errors in Y discrete duration measurement."
  ([^long connected-until-errcount ^long connected-until-duration]
    (make-discrete-fault-detector connected-until-errcount connected-until-duration {}))
  ([^long connected-until-errcount ^long connected-until-duration {:keys [now-finder]
                                                                   :or {now-finder u/now-millis}}]
    (let [fault-counter (m/make-integer-counter :count)
          start-tstamp  (volatile! (now-finder))
          reset-timer   #(locking fault-counter
                           (t/reinit! fault-counter)
                           (vreset! start-tstamp (now-finder)))
          refresh-timer #(let [ts @start-tstamp]
                           (when (>= ^long (now-finder) (unchecked-add ^long ts connected-until-duration))
                             (reset-timer)))]
      (reify
        t/IMetricsRecorder   (record! [_] (throw (IllegalArgumentException. "This should never be called")))
                             (record! [_ status?] (do
                                                    (refresh-timer)
                                                    (when-not status?
                                                      (t/record! fault-counter))))
        t/IReinitializable   (reinit! [_] (reset-timer))
        clojure.lang.Counted (count   [_] (do
                                            (refresh-timer)
                                            (count fault-counter)))
        clojure.lang.IDeref  (deref   [_] (do
                                            (refresh-timer)
                                            (deref fault-counter)))
        t/IFaultDetector     (fault?  [_] (do
                                            (refresh-timer)
                                            (>= (count fault-counter) connected-until-errcount)))))))


(defn make-rolling-fault-detector
  "Create a protocols-instance that detects faults based on threshold specified as connected-until-errcount errors in
  connected-until-duration. This follows the X errors in Y duration measurement.
  See also: preflex.metrics/make-rolling-integer-counter"
  ([^long connected-until-errcount ^long connected-until-duration]
    (make-rolling-fault-detector connected-until-errcount connected-until-duration {}))
  ([^long connected-until-errcount ^long connected-until-duration {:keys [^long bucket-interval]
                                                                   :or {bucket-interval 1000}
                                                                   :as options}]
    (when (or (not= 0 (rem connected-until-duration bucket-interval))
            (<= (quot connected-until-duration bucket-interval) 0))
      (in/expected "connected-until-duration to be a multiple of bucket-interval"
        {:connected-until connected-until-duration
         :bucket-interval bucket-interval}))
    (let [bucket-count  (quot connected-until-duration bucket-interval)
          fault-counter (m/make-rolling-integer-counter :count (inc bucket-count)
                          (merge options {:bucket-interval bucket-interval}))]
      (reify
        t/IMetricsRecorder   (record! [_] (throw (IllegalArgumentException. "This should never be called")))
                             (record! [_ status?] (when-not status?
                                                    (t/record! fault-counter)))
        t/IReinitializable   (reinit! [_] (t/reinit! fault-counter))
        clojure.lang.Counted (count   [_] (count fault-counter))
        clojure.lang.IDeref  (deref   [_] (deref fault-counter))
        t/IFaultDetector     (fault?  [_] (>= (count fault-counter) connected-until-errcount))))))


(defn make-half-open-retry-resolver
  "Make a retry-resolver that allows specified number of retries per every half-open window. Retries happen
  consecutively at the beginning of every half-open window. An 'open' window precedes all half-open windows.
  Options:
    :open-millis (int, default: same as half-open-millis) 'open' period preceding the half-open periods
    :retry-times (int, default: 1) max number of times to retry in every half-open window"
  ([^long half-open-duration]
    (make-half-open-retry-resolver half-open-duration {}))
  ([^long half-open-duration {:keys [now-finder
                                     open-duration
                                     retry-times]
                            :or {now-finder    u/now-millis
                                 open-duration half-open-duration
                                 retry-times   1}
                            :as options}]
    (in/expected integer? "arg half-open-duration to be an integer" half-open-duration)
    (in/expected integer? "option :open-duration to be an integer" open-duration)
    (in/expected #(and (integer? %) (pos? ^long %)) "option :retry-times to be a positive integer" retry-times)
    (let [init-fn #(let [ts (now-finder)] (im/->RetryState
                                            ts    ; :retry-init-ts
                                            false ; :open-elapsed?
                                            ts    ; :last-retry-ts
                                            0     ; :retry-counter
                                            ))
          v-state (volatile! (init-fn))
          ^Semaphore
          bi-sema (Semaphore. 1 false)  ; binary semaphore
          h-shift (fn [^long ts]
                    (vswap! v-state assoc
                      :open-elapsed? true
                      :last-retry-ts ts
                      :retry-counter 1)
                    true)]
      (reify
        clojure.lang.IDeref (deref   [_] @v-state)
        t/IReinitializable  (reinit! [_] (locking v-state
                                           (vreset! v-state (init-fn))))
        t/IRetryResolver    (retry?  [_] (if (.tryAcquire bi-sema)
                                           (try
                                             (locking v-state
                                               (let [^RetryState state @v-state
                                                     ts (now-finder)]
                                                 (if (:open-elapsed? state)
                                                   ;; half-open window may have elapsed, so test and shift to next one
                                                   (if (>= (- ^long ts (.-last-retry-ts state)) half-open-duration)
                                                     (h-shift ts)  ; shift to the next half-open window and return true
                                                     (let [rc (.-retry-counter state)]
                                                       (if (< rc ^long retry-times)
                                                         (do
                                                           (vswap! v-state assoc
                                                             :retry-counter (unchecked-inc rc))
                                                           true)
                                                         false)))
                                                   ;; open period is not known to be elapsed, so test it
                                                   (if (>= (- ^long ts (.-retry-init-ts state)) ^long open-duration)
                                                     (h-shift ts)  ; shift to half-open and return true
                                                     false))))
                                             (finally
                                               (.release bi-sema)))
                                           ;; could not obtain soft-lock, another thread may be working, so disengage
                                           false))))))


(defn make-circuit-breaker
  "Create a circuit breaker that is based on the following principles:
  * Circuit breaker can only be in either connected (C) or tripped (T) state.
  * Tripping happens based on fault-detector (preflex.type.IFaultDetector/fault?)
  * In tripped state, circuit breaker allows operation based on retry-resolver to see if the system has recovered.
  * A caller may invoke (preflex.type.ICircuitBreaker/mark!) informing about the status of an operation. Success
    sets circuit-breaker into connected state, whereas failure may set circuit breaker into tripped state.
  Options:
    :circuit-breaker-name (any type) circuit-breaker name, coerced as string
    :on-trip              (fn/1)     called when circuit breaker switches from connected to tripped state
    :on-connect           (fn/1)     called when circuit breaker switches from tripped to connected state"
  ([fault-detector retry-resolver {:keys [circuit-breaker-name
                                          on-trip
                                          on-connect]
                                   :or {circuit-breaker-name (gensym "circuit-breaker-")
                                        on-trip    in/nop
                                        on-connect in/nop}
                                   :as options}]
    (im/->DefaultCircuitBreaker
      (in/as-str circuit-breaker-name)
      (volatile! (im/->CircuitBreakerState
                   true ; start in a connected state
                   (u/now-millis)))
      fault-detector
      retry-resolver
      on-trip
      on-connect))
  ([fault-detector retry-resolver]
    (make-circuit-breaker fault-detector retry-resolver {})))


(defn via-circuit-breaker
  "Execute given task using specified circuit breaker.
  Options:
    :context-maker    fn/0 - creates context to be passed as first arg to other listeners
    :on-circuit-allow fn/1 - accepts context and circuit breaker, does nothing by default
    :on-circuit-deny  fn/1 - accepts circuit breaker, throws appropriate exception by default"
  ([circuit-breaker {:keys [context-maker
                            on-circuit-allow
                            on-circuit-deny]
                     :or {context-maker    im/make-context
                          on-circuit-allow in/nop
                          on-circuit-deny  (fn [_] (e/circuit-breaker-open))}
                     :as options}
    f]
    (let [ctx (context-maker circuit-breaker)]
      (if (t/allow? circuit-breaker)
        (do
          (on-circuit-allow ctx)
          (try
            (let [result (t/apply-noarg f)]
              (t/mark! circuit-breaker (t/success-result? f result))
              result)
            (catch Throwable e
              (t/mark! circuit-breaker (t/success-error? f e))
              (throw e))))
        (on-circuit-deny ctx))))
  ([circuit-breaker f]
    (via-circuit-breaker circuit-breaker {} f)))


;; ----- success/failure tracker -----


(defn via-success-failure-tracker
  "Execute given task using specified tracker, an arity-1 fn that accepts true to indicate success and false to
  indicate failure.
  Options:
    :context-maker fn/1 called with success-failure-tracker as argument
    :post-result   fn/2 called with context and the result being returned as arguments
    :post-error    fn/2 called with context and the error being thrown as arguments"
  ([success-failure-tracker {:keys [context-maker
                                    post-result
                                    post-error]
                             :or {context-maker im/make-context
                                  post-result   in/nop
                                  post-error    in/nop}}
    f]
    (let [ctx (context-maker success-failure-tracker)
          [result error] (in/maybe [Throwable] (t/apply-noarg f))]
      (when error
        (success-failure-tracker (t/success-error? f error))
        (post-error ctx error)
        (throw error))
      (success-failure-tracker (t/success-result? f result))
      (post-result ctx result)
      result))
  ([success-failure-tracker f]
    (via-success-failure-tracker success-failure-tracker {} f)))


;; ----- latency tracker -----


(defn via-latency-tracker
  "Execute given task using latency tracker, an arity-2 fn accepting success-status true/false and long-int latency.
  Options:
    :now-finder  (fn/1) fn returning stopwatch time now as long int"
  ([latency-tracker {:keys [now-finder]
                     :or {now-finder u/now-millis}}
    f]
    (let [start (long (now-finder))
          [result error] (in/maybe [Throwable] (t/apply-noarg f))
          latency (- (long (now-finder)) start)]
      (when error
        (latency-tracker (t/success-error? f error) latency)
        (throw error))
      (latency-tracker (t/success-result? f result) latency)
      result))
  ([latency-tracker f]
    (via-latency-tracker latency-tracker {} f)))


;; ----- fallback -----


(defn via-fallback
  "Given one or more tasks (each task is a no-arg fn/invokable) execute them serially such that the first successful
  result is returned. On failure invoke the next fn and so on. In the event of no success, return the last failure.
  Options:
    :context-maker fn/1 - creates context to be passed as first arg to other listeners
    :pre-invoke    fn/2 - accepts context and task as arguments, called before every task invocation
    :post-result   fn/3 - accepts context, task and final result as args, called when finally returning a result
    :post-error    fn/3 - accepts context, task and final error as args, called when finally throwing an error"
  ([fallback-fns {:keys [context-maker
                         pre-invoke
                         post-result
                         post-error]
                  :or {context-maker im/make-context
                       pre-invoke    in/nop
                       post-result   in/nop
                       post-error    in/nop}}
    f]
    (let [tasks (conj (vec fallback-fns) f)
          ctx (context-maker tasks)]
      (loop [fs tasks]
        (let [f (first fs)
              [result error] (do
                               (pre-invoke ctx f)
                               (in/maybe [Exception] (t/apply-noarg f)))]
          (if error
            (if (t/success-error? f error)
              (do
                (post-error ctx f error)
                (throw error))
              (if-let [gs (next fs)]
                (recur gs)
                (do
                  (post-error ctx f error)
                  (throw error))))
            (if (t/success-result? f result)
              (do
                (post-result ctx f result)
                result)
              (if-let [gs (next fs)]
                (recur gs)
                (do
                  (post-result ctx f result)
                  result))))))))
  ([fallback-fns f]
    (via-fallback fallback-fns {} f)))


;; ----- wrappers -----


(defn wrap-thread-pool
  "Wrap given fn with specified thread pool.
  See: preflex.core/via-thread-pool"
  ([thread-pool options f]
    (fn thread-pool-wrapper [& args]
      (via-thread-pool thread-pool options (iv/partial-invokable f args))))
  ([thread-pool f]
    (wrap-thread-pool thread-pool {} f)))


(defn wrap-semaphore
  "Wrap given fn (invokable) using specified semaphore.
  See: preflex.core/via-semaphore"
  ([semaphore options f]
    (fn semaphore-wrapper [& args]
      (via-semaphore semaphore options (iv/partial-invokable f args))))
  ([semaphore f]
    (wrap-semaphore semaphore {} f)))


(defn wrap-circuit-breaker
  "Wrap given fn with specified circuit breaker.
  See: preflex.core/via-circuit-breaker"
  ([circuit-breaker options f]
    (fn circuit-breaker-wrapper [& args]
      (via-circuit-breaker circuit-breaker options (iv/partial-invokable f args))))
  ([circuit-breaker f]
    (wrap-circuit-breaker circuit-breaker {} f)))


(defn wrap-success-failure-tracker
  "Wrap given fn (invokable) with specified tracker.
  See: preflex.core/via-success-failure-tracker"
  ([success-failure-tracker options f]
    (fn track-success-failure [& args]
      (via-success-failure-tracker success-failure-tracker (iv/partial-invokable f args))))
  ([success-failure-tracker f]
    (wrap-success-failure-tracker success-failure-tracker {} f)))


(defn wrap-latency-tracker
  "Wrap given fn (invokable) with latency tracker.
  See: preflex.core/via-latency-tracker"
  ([latency-tracker options f]
    (fn track-latency [& args]
      (via-latency-tracker latency-tracker options (iv/partial-invokable f args))))
  ([latency-tracker f]
    (wrap-latency-tracker latency-tracker {} f)))


(defn wrap-fallback
  "Wrap given fns (invokables) with fallback functions.
  See: preflex.core/via-fallback"
  ([fallback-fns options f]
    (fn fallback-wrapper [& args]
      (via-fallback
        (mapv #(iv/partial-invokable % args) fallback-fns)
        (iv/partial-invokable f args))))
  ([fallback-fns f]
    (wrap-fallback fallback-fns {} f)))


;; ----- macros -----


(defmacro with-thread-pool
  "Execute body of code asynchronously by submitting as a task to a thread-pool.
  See: preflex.core/via-thread-pool"
  [thread-pool options & body]
  `(via-thread-pool ~thread-pool ~options (^:once fn* [] ~@body)))


(defmacro with-semaphore
  "Execute given body of code using specified semaphore.
  See: preflex.core/via-semaphore"
  [semaphore options & body]
  `(via-semaphore ~semaphore ~options (^:once fn* [] ~@body)))


(defmacro with-circuit-breaker
  "Execute body of code using specified circuit breaker.
  See: preflex.core/via-circuit-breaker"
  [circuit-breaker options & body]
  `(via-circuit-breaker ~circuit-breaker ~options (^:once fn* [] ~@body)))


(defmacro with-success-failure-tracker
  "Execute body of code using specified tracker.
  See: preflex.core/via-success-failure-tracker"
  [success-failure-tracker options & body]
  `(via-success-failure-tracker ~success-failure-tracker ~options (^:once fn* [] ~@body)))


(defmacro with-latency-tracker
  "Execute body of code using latency tracker.
  See: preflex.core/via-latency-tracker"
  [latency-tracker options & body]
  `(via-latency-tracker ~latency-tracker ~options (^:once fn* [] ~@body)))


(defmacro with-fallback
  "Execute body of code using specified fallback functions.
  See: preflex.core/via-fallback"
  [fallback-fns options & body]
  `(via-fallback ~fallback-fns ~options (^:once fn* [] ~@body)))
