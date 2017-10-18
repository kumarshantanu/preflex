;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.resilient.hystrix-test
  (:require
    [clojure.test :refer :all]
    [preflex.resilient         :as r]
    [preflex.resilient.hystrix :as hystrix])
  (:import
    [preflex.type SampleMetrics]))


(deftest test-make-default-collectors
  (let [collectors (hystrix/make-default-collectors)
        {:keys [;; trackers
                latency-tracker
                success-failure-tracker
                ;; options
                success-failure-options
                circuit-breaker-options
                semaphore-options
                thread-pool-options
                metrics-collectors]} collectors
        reporter   (hystrix/make-metrics-reporter metrics-collectors)
        fd (r/make-rolling-fault-detector 20 [10000 :millis])
        rr (r/make-half-open-retry-resolver [5 :seconds])
        circuit-breaker     (r/make-circuit-breaker fd rr circuit-breaker-options)
        execution-semaphore (r/make-counting-semaphore 10 semaphore-options)
        thread-pool (r/make-bounded-thread-pool 10 20)
        o-reporter (hystrix/make-metrics-reporter metrics-collectors
                     {:circuit-breaker     circuit-breaker
                      :execution-semaphore execution-semaphore})]
    (is (map? collectors))
    (is (ifn? reporter))
    (is (map? (reporter)))
    (testing "Collectors used to construct resilience primitives"
      (is (map? @reporter))
      (is (ifn? o-reporter))
      (is (map? (o-reporter)))
      (is (map? @o-reporter)))
    (testing "Collectors used to collect metrics in resilience primitives"
      (testing "Latency tracker"
        (r/via-latency-tracker latency-tracker {} #(Thread/sleep 1000))
        (r/via-latency-tracker latency-tracker {} #(Thread/sleep 1000))
        (let [execute-latency-summary (get @(:execute-latency metrics-collectors) :execute-latency)]
          (is (instance? SampleMetrics execute-latency-summary))))
      (testing "Success-failure tracker"
        (r/via-success-failure-tracker success-failure-tracker success-failure-options
          #(+ 10 20))
        (is (thrown? Exception
              (r/via-success-failure-tracker success-failure-tracker success-failure-options
                #(throw (Exception. "test error")))))
        (let [{:keys [cumulative-count-success cumulative-count-failure
                      rolling-count-success rolling-count-failure]} @(:success-failure metrics-collectors)]
          (is (every? integer? [cumulative-count-success cumulative-count-failure
                                rolling-count-success rolling-count-failure]))
          (is (= 1 cumulative-count-success))
          (is (= 1 cumulative-count-failure))))
      (testing "Execution semaphore"
        (r/via-semaphore execution-semaphore semaphore-options #(+ 10 20))
        (is (every? #(zero? ^long (get @(:semaphore-reject metrics-collectors) %))
              [:cumulative-count-semaphore-rejected
               :rolling-count-semaphore-rejected])))
      (testing "Thread-pool execution"
        (r/via-thread-pool thread-pool #(+ 10 20))
        (is (every? #(integer? (get @(:thread-pool-reject metrics-collectors) %))
              [:cumulative-count-thread-pool-rejected
               :rolling-count-thread-pool-rejected]))
        (is (every? #(integer? (get @(:thread-pool-timeout metrics-collectors) %)) [:cumulative-count-timeout
                                                                                    :rolling-count-timeout])))
      (testing "Circuit breaker execution"
        (r/via-circuit-breaker circuit-breaker circuit-breaker-options #(+ 10 20))
        (is (every? #(zero? ^long (get @(:short-circuited metrics-collectors) %))
              [:cumulative-count-short-circuited
               :rolling-count-short-circuited]))))))


(deftest test-command-metrics
  )


(deftest test-thread-pool-metrics
  )
