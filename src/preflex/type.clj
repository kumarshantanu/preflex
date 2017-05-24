;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.type
  "Common type definitions
  General:
  * Invokable         - an invokable (like a function) whose result can be tested for success/failure
  Resilience:
  * IBoundedQueueInfo - provides information regarding a bounded queue
  * ISemaphore        - a semaphore interface
  * ICircuitBreaker   - a circuit breaker interface
  Metrics:
  * IMetricsRecorder  - an interface for metrics event capturing
  * IMetricsStore     - a metrics store interface
  * SampleMetrics     - stats for sample metrics data"
  (:import
    [java.util List Map]
    [java.util.concurrent TimeUnit]))


;; ----- invokable -----


(defprotocol Invokable
  (apply-noarg     [this]        "Execute as if it is a no-arg function")
  (apply-arguments [this args]   "Apply arguments as if it is a function")
  (success-result? [this result] "Return true if result indicates success, false otherwise")
  (success-error?  [this error]  "Return true if error indicates success, false otherwise"))


(extend-protocol Invokable
  clojure.lang.AFn
  (apply-noarg     [this]        (this))
  (apply-arguments [this args]   (apply this args))
  (success-result? [this result] true)
  (success-error?  [this error]  false))


;; ----- utility -----


(defprotocol IDuration
  (^long     duration-time [this] "Time duration")
  (^TimeUnit duration-unit [this] "Time unit"))


;; ----- resilience -----


(defprotocol IBoundedQueueInfo
  (queue-capacity [this] "Return maximum capacity of a bounded queue")
  (queue-size     [this] "Return the current size of a bounded queue"))


(defprotocol ISemaphore
  (acquire-permit! [this] [this timeout unit] "Return true if successfully acquired permit, false otherwise")
  (release-permit! [this] "Release permit and return nil")
  (shutdown!       [this] "Initiate an orderly shutdown in which no new permits can be acquired")
  (count-acquired  [this] "Return a count of permits in use")
  (count-available [this] "Return a count of available pemits"))


(defprotocol IFaultDetector  ; for circuit-breaker
  (fault? [this] "Return true if fault is detected, false otherwise"))


(defprotocol IRetryResolver  ; for circuit-breaker (and perhaps more)
  (retry? [this] "Return true to allow retry, false otherwise"))


(defprotocol ICircuitBreaker
  (allow? [this] "Return true if operation allowed, false otherwise")
  (mark!  [this status?] "Record operation result as true (success) or false (failure)"))


;; ----- metrics -----


(defprotocol IMetricsRecorder
  (record! [this] [this v] "Record metrics event. Not guaranteed to be synchronous."))


(defprotocol IMetricsStore
  (^IMetricsRecorder get-collector [this category name k] "Return metrics collector for given category, name and key"))


(defprotocol IReinitializable
  (reinit! [this] "Reset the configuration. Not guaranteed to be synchronous."))


(defrecord SampleMetrics
  [^int max ^double mean ^double median ^int min percentiles])
