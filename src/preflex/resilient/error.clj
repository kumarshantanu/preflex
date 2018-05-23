;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.resilient.error)


(def managed-failure ::managed-failure)
(def cause-circuit-breaker-open ::circuit-breaker-open)
(def cause-exception-occurred   ::exception-occurred)
(def cause-semaphore-rejected   ::semaphore-rejected)
(def cause-operation-timed-out  ::operation-timed-out)
(def cause-thread-pool-rejected ::thread-pool-rejected)


(defn circuit-breaker-open
  []
  (throw (ex-info "Circuit-breaker is open" {managed-failure cause-circuit-breaker-open})))


(defn exception-occurred
  [^Throwable e]
  (throw (ex-info "Exception occurred" {managed-failure cause-exception-occurred} e)))


(defn semaphore-rejected
  []
  (throw (ex-info "Semaphore rejected execution" {managed-failure cause-semaphore-rejected})))


(defn operation-timed-out
  []
  (throw (ex-info "Operation timed out" {managed-failure cause-operation-timed-out})))


(defn thread-pool-rejected
  []
  (throw (ex-info "Thread-pool rejected execution" {managed-failure cause-thread-pool-rejected})))


(defn rethrow
  ([e]
    (throw e))
  ([_ e]
    (throw e)))
