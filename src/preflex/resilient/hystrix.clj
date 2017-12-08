;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.resilient.hystrix
  "Emulation of Hystrix command/metrics.
  See:
  1. https://github.com/Netflix/Hystrix
  2. https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring
  3. https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard
  4. https://github.com/Netflix/Hystrix/wiki/Dashboard"
  (:require
    [preflex.metrics        :as m]
    [preflex.resilient      :as r]
    [preflex.resilient.impl :as rimpl]
    [preflex.type           :as type]
    [preflex.util           :as u])
  (:import
    [java.util.concurrent BlockingQueue ThreadPoolExecutor]
    [clojure.lang IDeref IFn]
    [preflex.resilient.impl CircuitBreakerState DefaultCircuitBreaker]))


(defrecord HystrixCommandMetrics
  [;; ID
   type #_"HystrixCommand"
   name
   group
   ;; ----------
   ;; Informational and Status
   ;; ----------
   #_Boolean isCircuitBreakerOpen
   #_Number errorCount       ; undocumented, but required
   #_Number requestCount     ; undocumented, but required
   #_Number errorPercentage
   #_Number executionSemaphorePermitsInUse
   #_String commandGroup
   #_Number currentTime
   ;; ----------
   ;; Cumulative Counts (Counter)
   ;; The following represent cumulative counts since the start of the application.
   ;; ----------
   #_Long countCollapsedRequests
   #_Long countExceptionsThrown
   #_Long countFailure
   #_Long countFallbackFailure
   #_Long countFallbackRejection
   #_Long countFallbackSuccess
   #_Long countResponsesFromCache
   #_Long countSemaphoreRejected
   #_Long countShortCircuited
   #_Long countSuccess
   #_Long countThreadPoolRejected
   #_Long countTimeout
   ;; ----------
   ;; Rolling Counts (Gauge)
   ;; These are “point in time” counts representing the last x seconds (for example 10 seconds).
   ;; The following are rolling counts as configured by metrics.rollingStats.* properties.
   ;; ----------
   #_Number rollingCountCollapsedRequests
   #_Number rollingCountExceptionsThrown
   #_Number rollingCountFailure
   #_Number rollingCountFallbackFailure
   #_Number rollingCountFallbackRejection
   #_Number rollingCountFallbackSuccess
   #_Number rollingCountResponsesFromCache
   #_Number rollingCountSemaphoreRejected
   #_Number rollingCountShortCircuited
   #_Number rollingCountSuccess
   #_Number rollingCountThreadPoolRejected
   #_Number rollingCountTimeout
   #_Number currentConcurrentExecutionCount  ; undocumented, but required
   ;; ----------
   ;; Latency Percentiles: HystrixCommand.run() Execution (Gauge)
   ;; These metrics represent percentiles of execution times for the HystrixCommand.run() method (on the child thread if using thread isolation).
   ;; These are rolling percentiles as configured by metrics.rollingPercentile.* properties.
   ;; ----------
   #_Number latencyExecute_mean
   #_Map latencyExecute
   ;; ----------
   ;; Latency Percentiles: End-to-End Execution (Gauge)
   ;; These metrics represent percentiles of execution times for the end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until a response is returned (or is ready to return in case of queue()).
   ;; The purpose of this compared with the latencyExecute* percentiles is to measure the cost of thread queuing/scheduling/execution, semaphores, circuit breaker logic, and other aspects of overhead (including metrics capture itself).
   ;; These are rolling percentiles as configured by metrics.rollingPercentile.* properties.
   ;; ----------
   #_Number latencyTotal_mean
   #_Map latencyTotal
   ;; ----------
   ;; Property Values (Informational)
   ;; These informational metrics report the actual property values being used by the HystrixCommand. This enables you to see when a dynamic property takes effect and to confirm a property is set as expected.
   ;; Number propertyValue_rollingStatisticalWindowInMilliseconds
   ;; ----------
   #_Number  propertyValue_circuitBreakerRequestVolumeThreshold
   #_Number  propertyValue_circuitBreakerSleepWindowInMilliseconds
   #_Number  propertyValue_circuitBreakerErrorThresholdPercentage
   #_Boolean propertyValue_circuitBreakerForceOpen
   #_Boolean propertyValue_circuitBreakerForceClosed
   #_Number  propertyValue_executionIsolationThreadTimeoutInMilliseconds
   #_String  propertyValue_executionIsolationStrategy
   #_Boolean propertyValue_executionIsolationThreadInterruptOnTimeout
   #_Boolean propertyValue_metricsRollingPercentileEnabled
   #_Number  propertyValue_metricsRollingStatisticalWindowInMilliseconds
   #_Boolean propertyValue_requestCacheEnabled
   #_Boolean propertyValue_requestLogEnabled
   #_Number  propertyValue_executionIsolationSemaphoreMaxConcurrentRequests
   #_Number  propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests])


(def hystrix-latency-percentiles [5 25 50 75 90 99 99.5])


(defn make-command-metrics-collectors
  "Make the default collectors as options for resilient primitives, required for Hystrix reporting."
  ([]
    (make-command-metrics-collectors {}))
  ([{:keys [bucket-count
            now-finder
            percentiles]
     :or {bucket-count 11
          now-finder   u/now-millis
          percentiles  hystrix-latency-percentiles}}]
    (let [;; success-failure tracking
          success-failure     (m/make-union-collector
                                [(m/make-boolean-counter :cumulative-count-success :cumulative-count-failure)
                                 (m/make-rolling-boolean-counter :rolling-count-success :rolling-count-failure
                                   bucket-count {:event-id-fn now-finder})])
          exceptions-thrown   (m/make-union-collector
                                [(m/make-integer-counter :cumulative-count-exceptions-thrown)
                                 (m/make-rolling-integer-counter :rolling-count-exceptions-thrown bucket-count
                                   {:event-id-fn now-finder})])
          ;; semaphores
          semaphore-reject    (m/make-union-collector
                                [(m/make-integer-counter :cumulative-count-semaphore-rejected)
                                 (m/make-rolling-integer-counter :rolling-count-semaphore-rejected bucket-count
                                   {:event-id-fn now-finder})])
          ;; thread-pool
          thread-pool-reject  (m/make-union-collector
                                [(m/make-integer-counter :cumulative-count-thread-pool-rejected)
                                 (m/make-rolling-integer-counter :rolling-count-thread-pool-rejected bucket-count
                                   {:event-id-fn now-finder})])
          thread-pool-timeout (m/make-union-collector
                                [(m/make-integer-counter :cumulative-count-timeout)
                                 (m/make-rolling-integer-counter :rolling-count-timeout bucket-count
                                   {:event-id-fn now-finder})])
          ;; circuit-breaker
          short-circuited     (m/make-union-collector
                                [(m/make-integer-counter :cumulative-count-short-circuited)
                                 (m/make-rolling-integer-counter :rolling-count-short-circuited bucket-count
                                   {:event-id-fn now-finder})])
          ;; latency tracking
          execute-latency     (m/make-rolling-percentile-collector :execute-latency percentiles bucket-count
                                {:event-id-fn now-finder})
          total-latency       (m/make-rolling-percentile-collector :total-latency   percentiles bucket-count
                                {:event-id-fn now-finder})]
      {:metrics-collectors {:success-failure     success-failure
                            :exceptions-thrown   exceptions-thrown
                            :semaphore-reject    semaphore-reject
                            :thread-pool-reject  thread-pool-reject
                            :thread-pool-timeout thread-pool-timeout
                            :short-circuited     short-circuited
                            :execute-latency     execute-latency
                            :total-latency       total-latency}
       :latency-tracker         (fn [status? ^long latency] (type/record! execute-latency latency))
       :success-failure-tracker (fn [status?] (type/record! success-failure status?))
       :success-failure-options {:post-error          (fn [context ex] (type/record! exceptions-thrown))}
       :semaphore-options       {:on-semaphore-reject (fn [context]    (type/record! semaphore-reject))}
       :thread-pool-options     {:on-task-reject      (fn [context ex] (type/record! thread-pool-reject))
                                 :on-task-timeout     (fn [context ex] (type/record! thread-pool-timeout))}
       :circuit-breaker-options {:on-circuit-deny     (fn [context]    (type/record! short-circuited))}})))


(defn make-command-metrics-reporter
  ([metrics-collectors]
    (make-command-metrics-reporter metrics-collectors {}))
  ([metrics-collectors {:keys [execution-semaphore
                               circuit-breaker]}]
    (let [{:keys [success-failure
                  exceptions-thrown
                  semaphore-reject
                  short-circuited
                  thread-pool-reject
                  thread-pool-timeout
                  execute-latency
                  total-latency]} metrics-collectors
          ;; new reporters
          circuit-breaker   (if (some? circuit-breaker)
                              (reify IDeref (deref [_] {:circuit-breaker-open?
                                                        (let [st (.-volatile-circuit-breaker-state
                                                                   ^DefaultCircuitBreaker circuit-breaker)]
                                                          (not (.-state-connected? ^CircuitBreakerState @st)))}))
                              (reify IDeref (deref [_] {:circuit-breaker-open? false})))
          error-percentage  (reify IDeref
                              (deref [_] (let [success-failure-count (deref success-failure)
                                               success-count (:cumulative-count-success success-failure-count)
                                               failure-count (:cumulative-count-failure success-failure-count)
                                               request-count (+ ^long success-count ^long failure-count)]
                                           (merge success-failure-count
                                             {:error-count      failure-count
                                              :request-count    request-count
                                              :error-percentage (if (zero? ^long failure-count)
                                                                  0
                                                                  (double (/ (* 100 ^long failure-count)
                                                                            request-count)))}))))
          exec-semaphore    (if (some? execution-semaphore)
                              (reify IDeref (deref [_] {:execution-semaphore-permits-in-use (type/count-acquired
                                                                                              execution-semaphore)}))
                              (reify IDeref (deref [_] {:execution-semaphore-permits-in-use 0})))
          metrics-reporter  (fn [] (->> [exceptions-thrown
                                         semaphore-reject
                                         short-circuited
                                         thread-pool-reject
                                         thread-pool-timeout
                                         execute-latency
                                         total-latency
                                         ;; new reporters
                                         circuit-breaker
                                         error-percentage
                                         exec-semaphore]
                                     (map deref)
                                     (apply merge)))]
      (reify
        IFn
        (invoke  [_]     (metrics-reporter))
        (invoke  [_ x]   ((metrics-reporter) x))
        (invoke  [_ x y] ((metrics-reporter) x y))
        (applyTo [_ c]   (apply (metrics-reporter) c))
        IDeref
        (deref   [_]     (metrics-reporter))))))


(defn make-hystrix-command-metrics-source
  "Given command name, metrics reporter fn `(fn []) -> map` and fallback metric reporter fn `(fn []) -> map` construct
  and return a fn (fn []) -> HystrixCommandMetrics."
  [command-name metrics-reporter fallback-metrics-reporter]
  (fn []
    (let [{:keys [;; -- cumulative counts --
                  cumulative-count-success
                  cumulative-count-exceptions-thrown
                  cumulative-count-failure
                  cumulative-count-semaphore-rejected
                  cumulative-count-short-circuited
                  cumulative-count-thread-pool-rejected
                  cumulative-count-timeout
                  ;; -- rolling counts --
                  rolling-count-success
                  rolling-count-exceptions-thrown
                  rolling-count-failure
                  rolling-count-semaphore-rejected
                  rolling-count-short-circuited
                  rolling-count-thread-pool-rejected
                  rolling-count-timeout
                  ;; -- rolling latency percentiles --
                  execute-latency
                  total-latency
                  ;; -- other keys --
                  circuit-breaker-open?
                  error-count
                  request-count
                  error-percentage
                  execution-semaphore-permits-in-use]
           :as command-metrics} (metrics-reporter)
          execute-latency (or execute-latency {:mean 0 :percentiles {}})
          total-latency   (or total-latency   {:mean 0 :percentiles {}})
          fallback-metrics (fallback-metrics-reporter)
          cumulative-fallback-success (:cumulative-count-success fallback-metrics)
          cumulative-fallback-failure (:cumulative-count-failure fallback-metrics)
          cumulative-fallback-rejection (+ ^long (:cumulative-count-semaphore-rejected fallback-metrics)
                                          ^long (:cumulative-count-thread-pool-rejected fallback-metrics))
          rolling-fallback-success (:rolling-count-success fallback-metrics)
          rolling-fallback-failure (:rolling-count-failure fallback-metrics)
          rolling-fallback-rejection (+ ^long (:rolling-count-semaphore-rejected fallback-metrics)
                                          ^long (:rolling-count-thread-pool-rejected fallback-metrics))]
     (->HystrixCommandMetrics
       ;; ID
       "HystrixCommand" ; type
       command-name     ; name
       command-name     ; group (use name as group as we do not have the group concept)
       ;; ----------
       ;; Informational and Status
       ;; ----------
       circuit-breaker-open? ; #_Boolean isCircuitBreakerOpen
       error-count           ; #_Number errorCount
       request-count         ; #_Number requestCount
       error-percentage      ; #_Number errorPercentage
       execution-semaphore-permits-in-use ; #_Number executionSemaphorePermitsInUse
       command-name   ; #_String commandGroup
       (u/now-millis) ; #_Number currentTime
       ;; ----------
       ;; Cumulative Counts (Counter)
       ;; The following represent cumulative counts since the start of the application.
       ;; ----------
       0 ; #_Long countCollapsedRequests
       cumulative-count-exceptions-thrown    ; #_Long countExceptionsThrown
       cumulative-count-failure              ; #_Long countFailure
       cumulative-fallback-failure           ; #_Long countFallbackFailure
       cumulative-fallback-rejection         ; #_Long countFallbackRejection
       cumulative-fallback-success           ; #_Long countFallbackSuccess
       0 ; #_Long countResponsesFromCache
       cumulative-count-semaphore-rejected   ; #_Long countSemaphoreRejected
       cumulative-count-short-circuited      ; #_Long countShortCircuited
       cumulative-count-success              ; #_Long countSuccess
       cumulative-count-thread-pool-rejected ; #_Long countThreadPoolRejected
       cumulative-count-timeout              ; #_Long countTimeout
       ;; ----------
       ;; Rolling Counts (Gauge)
       ;; These are “point in time” counts representing the last x seconds (for example 10 seconds).
       ;; The following are rolling counts as configured by metrics.rollingStats.* properties.
       ;; ----------
       0 ; #_Number rollingCountCollapsedRequests
       rolling-count-exceptions-thrown    ; #_Number rollingCountExceptionsThrown
       rolling-count-failure              ; #_Number rollingCountFailure
       rolling-fallback-failure           ; #_Number rollingCountFallbackFailure
       rolling-fallback-rejection         ; #_Number rollingCountFallbackRejection
       rolling-fallback-success           ; #_Number rollingCountFallbackSuccess
       0 ; #_Number rollingCountResponsesFromCache
       rolling-count-semaphore-rejected   ; #_Number rollingCountSemaphoreRejected
       rolling-count-short-circuited      ; #_Number rollingCountShortCircuited
       rolling-count-success              ; #_Number rollingCountSuccess
       rolling-count-thread-pool-rejected ; #_Number rollingCountThreadPoolRejected
       rolling-count-timeout              ; #_Number rollingCountTimeout
       0 ; #_Number currentConcurrentExecutionCount ; FIXME put actual count
       ;; ----------
       ;; Latency Percentiles: HystrixCommand.run() Execution (Gauge)
       ;; These metrics represent percentiles of execution times for the HystrixCommand.run() method (on the child thread if using thread isolation).
       ;; These are rolling percentiles as configured by metrics.rollingPercentile.* properties.
       ;; ----------
       (:mean execute-latency) ; #_Number latencyExecute_mean
       (:percentiles execute-latency) ; #_Map latencyExecute
       ;; ----------
       ;; Latency Percentiles: End-to-End Execution (Gauge)
       ;; These metrics represent percentiles of execution times for the end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until a response is returned (or is ready to return in case of queue()).
       ;; The purpose of this compared with the latencyExecute* percentiles is to measure the cost of thread queuing/scheduling/execution, semaphores, circuit breaker logic, and other aspects of overhead (including metrics capture itself).
       ;; These are rolling percentiles as configured by metrics.rollingPercentile.* properties.
       ;; ----------
       (:mean total-latency) ; #_Number latencyTotal_mean
       (:percentiles total-latency) ; #_Number latencyTotal
       ;; ----------
       ;; Property Values (Informational)
       ;; These informational metrics report the actual property values being used by the HystrixCommand. This enables you to see when a dynamic property takes effect and to confirm a property is set as expected.
       ;; Number propertyValue_rollingStatisticalWindowInMilliseconds
       ;; ----------
       -1     ; #_Number  propertyValue_circuitBreakerRequestVolumeThreshold
       -1     ; #_Number  propertyValue_circuitBreakerSleepWindowInMilliseconds
       -1     ; #_Number  propertyValue_circuitBreakerErrorThresholdPercentage
       false  ; #_Boolean propertyValue_circuitBreakerForceOpen
       false  ; #_Boolean propertyValue_circuitBreakerForceClosed
       -1     ; #_Number  propertyValue_executionIsolationThreadTimeoutInMilliseconds
       "none" ; #_String  propertyValue_executionIsolationStrategy
       false  ; #_Boolean propertyValue_executionIsolationThreadInterruptOnTimeout
       true   ; #_Boolean propertyValue_metricsRollingPercentileEnabled
       1000   ; #_Number  propertyValue_metricsRollingStatisticalWindowInMilliseconds
       false  ; #_Boolean propertyValue_requestCacheEnabled
       false  ; #_Boolean propertyValue_requestLogEnabled
       -1     ; #_Number  propertyValue_executionIsolationSemaphoreMaxConcurrentRequests
       -1     ; #_Number  propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests
       ))))


(defrecord HystrixThreadPoolMetrics
  [;; ----------
   ;; Informational and Status
   ;; ----------
   #_String type #_"HystrixThreadPool"
   #_String name
   #_Number currentTime
   ;; ----------
   ;; Rolling Counts (Gauge)
   ;; ----------
   #_Number rollingMaxActiveThreads
   #_Number rollingCountThreadsExecuted
   ;; ----------
   ;; ThreadPool State (Gauge)
   ;; ----------
   #_Long currentPoolSize
   #_Long currentCorePoolSize
   #_Long currentMaximumPoolSize
   #_Long currentActiveCount         ; number of active threads
   #_Long currentCompletedTaskCount  ; total tasks completed so far
   #_Long currentLargestPoolSize
   #_Long currentTaskCount           ; total task count so far
   #_Long currentQueueSize
   ;; ----------
   ;; Property Values (Informational)
   ;; ----------
   #_Long propertyValue_queueSizeRejectionThreshold
   #_Long propertyValue_metricsRollingStatisticalWindowInMilliseconds])


(defn make-thread-pool-metrics-collectors
  "Make the default collectors as options for resilient primitives, required for Hystrix reporting."
  ([]
    (make-thread-pool-metrics-collectors {}))
  ([{:keys [bucket-count
            now-finder]
     :or {bucket-count 11
          now-finder   u/now-millis}}]
    (let [max-active-threads (m/make-rolling-max-collector :rolling-count-max-active-threads bucket-count
                               {:event-id-fn now-finder})
          threads-executed   (m/make-rolling-integer-counter :rolling-count-threads-executed bucket-count
                               {:event-id-fn now-finder})
          threads-rejected   (m/make-rolling-integer-counter :rolling-count-threads-rejected bucket-count
                               {:event-id-fn now-finder})]
      {:metrics-collectors  {:max-active-threads max-active-threads
                             :threads-executed   threads-executed}
       :thread-pool-options {:on-task-submit (fn [context]
                                               (type/record! max-active-threads)
                                               (type/record! threads-executed))
                             :on-task-reject (fn [context ex]
                                               (type/record! threads-rejected))}})))


(defn make-thread-pool-metrics-reporter
  ([metrics-collectors]
    (make-thread-pool-metrics-reporter metrics-collectors {}))
  ([metrics-collectors ^ThreadPoolExecutor thread-pool]
    (let [{:keys [max-active-threads
                  threads-executed]} metrics-collectors
          ;; new reporters
          thread-pool-stats (reify IDeref (deref [_] {:pool-size            (.getPoolSize           thread-pool)
                                                      :core-pool-size       (.getCorePoolSize       thread-pool)
                                                      :max-pool-size        (.getMaximumPoolSize    thread-pool)
                                                      :active-count         (.getActiveCount        thread-pool)
                                                      :completed-task-count (.getCompletedTaskCount thread-pool)
                                                      :largest-pool-size    (.getLargestPoolSize    thread-pool)
                                                      :task-count           (.getTaskCount          thread-pool)
                                                      :queue-size           (.size
                                                                              ^BlockingQueue (.getQueue thread-pool))}))
          metrics-reporter  (fn [] (->> [max-active-threads
                                         threads-executed
                                         thread-pool-stats]
                                     (mapv deref)
                                     (apply merge)))]
      (reify
        IFn
        (invoke  [_]     (metrics-reporter))
        (invoke  [_ x]   ((metrics-reporter) x))
        (invoke  [_ x y] ((metrics-reporter) x y))
        (applyTo [_ c]   (apply (metrics-reporter) c))
        IDeref
        (deref   [_]     (metrics-reporter))))))


(defn make-hystrix-thread-pool-metrics-source
  "Given thread-pool name and thread-pool metrics reporter fn `(fn []) -> map` construct and return a fn
  `(fn []) -> HystrixThreadPoolMetrics`."
  [thread-pool-name thread-pool-metrics-reporter]
  (fn []
    (let [{:keys [rolling-count-max-active-threads
                  rolling-count-threads-executed
                  pool-size
                  core-pool-size
                  max-pool-size
                  active-count
                  completed-task-count
                  largest-pool-size
                  task-count
                  queue-size]} (thread-pool-metrics-reporter)]
      (->HystrixThreadPoolMetrics
        ;; ----------
        ;; Informational and Status
        ;; ----------
        "HystrixThreadPool" ; #_String type #_"HystrixThreadPool"
        thread-pool-name    ; #_String name
        (u/now-millis)      ; #_Number currentTime
        ;; ----------
        ;; Rolling Counts (Gauge)
        ;; ----------
        rolling-count-max-active-threads  ; #_Number rollingMaxActiveThreads
        rolling-count-threads-executed    ; #_Number rollingCountThreadsExecuted
        ;; ----------
        ;; ThreadPool State (Gauge)
        ;; ----------
        pool-size            ; #_Long currentPoolSize
        core-pool-size       ; #_Long currentCorePoolSize
        max-pool-size        ; #_Long currentMaximumPoolSize
        active-count         ; #_Long currentActiveCount         ; number of active threads
        completed-task-count ; #_Long currentCompletedTaskCount  ; total tasks completed so far
        largest-pool-size    ; #_Long currentLargestPoolSize
        task-count           ; #_Long currentTaskCount           ; total task count so far
        queue-size           ; #_Long currentQueueSize
        ;; ----------
        ;; Property Values (Informational)
        ;; ----------
        -1 ; #_Long propertyValue_queueSizeRejectionThreshold
        -1 ; #_Long propertyValue_metricsRollingStatisticalWindowInMilliseconds
        ))))
