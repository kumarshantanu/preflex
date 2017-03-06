;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.impl
  "This namespace is internal and subject to change across releases without notice."
  (:require
    [preflex.type :as t]
    [preflex.util :as u])
  (:import
    [java.util.concurrent
     ArrayBlockingQueue BlockingQueue Callable ExecutionException Executor ExecutorService Future Semaphore
     ThreadPoolExecutor TimeUnit RejectedExecutionException TimeoutException]
    [clojure.lang IDeref Named]))


(defn make-context
  "Defaut context maker."
  [target]
  {:target target
   :stage (volatile! 0)
   :since-nanos (u/now-nanos)})


;; ----- resiliency type implementations -----


(defrecord BoundedThreadPool
  [^String thread-pool-name ^ThreadPoolExecutor thread-pool ^int queue-capacity]
  Named
  (getNamespace     [_] nil)
  (getName          [_] thread-pool-name)
  Executor
  (execute          [_ task]               (.execute          thread-pool (t/apply-noarg task)))
  ExecutorService
  (awaitTermination [_ timeout unit]       (.awaitTermination thread-pool timeout unit))
  (invokeAll        [_ tasks]              (.invokeAll        thread-pool tasks))
  (invokeAll        [_ tasks timeout unit] (.invokeAll        thread-pool tasks timeout unit))
  (invokeAny        [_ tasks]              (.invokeAny        thread-pool tasks))
  (invokeAny        [_ tasks timeout unit] (.invokeAny        thread-pool tasks timeout unit))
  (isShutdown       [_]                    (.isShutdown       thread-pool))
  (isTerminated     [_]                    (.isTerminated     thread-pool))
  (shutdown         [_]                    (.shutdown         thread-pool))
  (shutdownNow      [_]                    (.shutdownNow      thread-pool))
  (^Future submit   [_ ^Callable task]     (.submit           thread-pool task))
  (^Future submit   [_ ^Runnable task]     (.submit           thread-pool task))
  (submit           [_ task result]        (.submit           thread-pool task result))
  t/IBoundedQueueInfo
  (queue-capacity   [_]                    queue-capacity)
  (queue-size       [_]                    (.size ^BlockingQueue (.getQueue thread-pool))))


(defn bounded-thread-pool?
  "Return true if the argument is a bounded thread pool, false otherwise."
  [x]
  (instance? BoundedThreadPool x))


(deftype CountingSemaphore
  [^String semaphore-name
   ^Semaphore semaphore
   ^{:volatile-mutable true :tag "boolean"} shutdown?
   ^int max-capacity]
  Named
  (getNamespace     [_] nil)
  (getName          [_] semaphore-name)
  t/ISemaphore
  (acquire-permit! [_]              (if shutdown? false (.tryAcquire semaphore)))
  (acquire-permit! [_ timeout unit] (if shutdown? false (.tryAcquire semaphore timeout (u/resolve-time-unit unit))))
  (release-permit! [_]              (.release semaphore))
  (shutdown!       [_]              (set! shutdown? (boolean true)))
  (count-acquired  [_]              (- max-capacity (.availablePermits semaphore)))
  (count-available [_]              (.availablePermits semaphore)))


(defn counting-semaphore?
  "Return true if the argument is a counting semaphore, false otherwise."
  [x]
  (instance? CountingSemaphore x))


(defrecord RetryState
  [^long retry-init-ts
   ^boolean open-elapsed?
   ^long last-retry-ts
   ^long retry-counter])


(defrecord CircuitBreakerState
  [^boolean state-connected? ; state: connected (true) or tripped (false)
   ^long state-since-millis  ; timestamp (millis since epoch) for the state
   ])


(defrecord DefaultCircuitBreaker
  [^String circuit-breaker-name   ; name of the circuit breaker
   volatile-circuit-breaker-state ; a CircuitBreakerState instance (see above)
   fault-detector                 ; stateful fault detector
   retry-resolver                 ; stateful retry resolver
   on-trip    ; arity-1 fn
   on-connect ; arity-fn
   ]
  IDeref
  (deref [this] @volatile-circuit-breaker-state)
  Named
  (getNamespace     [_] nil)
  (getName          [_] circuit-breaker-name)
  t/ICircuitBreaker
  (allow? [this]
    (let [cb-state @volatile-circuit-breaker-state]
      (if (:state-connected? cb-state)
        ;; connected, so check whether error-count has exceeded threshold - if yes, then switch to tripped
        (if (t/fault? fault-detector)
          (let [ts (u/now-millis)]
            (locking this
              (if (t/fault? fault-detector)
                (do
                  ;; trip the circuit and set state to "open"
                  (vswap! volatile-circuit-breaker-state assoc
                    :state-connected? false
                    :state-since-millis ts)
                  (t/reinit! retry-resolver)
                  (on-trip this)
                  false)
                true)))
          true)
        ;; tripped, so check whether it is time to retry (again, since last retry)
        (t/retry? retry-resolver))))
  (mark! [this status?]
    (if status?
      ;; it is success, so we should put circuit-breaker back into connected state (if not already)
      (if (:state-connected? @volatile-circuit-breaker-state) ; tripped state, so we should switch to connected
        (t/record! fault-detector true)
        (do
          (t/reinit! fault-detector)
          (let [ts (u/now-millis)]
            (locking this
              (when-not (:state-connected? @volatile-circuit-breaker-state)
                (vswap! volatile-circuit-breaker-state assoc
                  :state-connected? true
                  :state-since-millis ts)
                (on-connect this)
                nil)))))
      ;; status is failure, so record failure if connected (do nothing if tripped)
      (when (:state-connected? @volatile-circuit-breaker-state)
        ;; record failure for connected state
        (t/record! fault-detector false)
        nil))))


(defn default-circuit-breaker?
  [x]
  (instance? DefaultCircuitBreaker x))
