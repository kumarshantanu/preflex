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
    [preflex.resilient.hystrix :as hystrix]))


(deftest test-make-default-collectors
  (let [collectors (hystrix/make-default-collectors)
        {:keys [circuit-breaker-options
                semaphore-options
                metrics-collectors]} collectors
        reporter   (hystrix/make-metrics-reporter metrics-collectors)
        fd (r/make-rolling-fault-detector 20 [10000 :millis])
        rr (r/make-half-open-retry-resolver [5 :seconds])
        o-reporter (hystrix/make-metrics-reporter (:metrics-collectors collectors)
                     {:circuit-breaker     (r/make-circuit-breaker fd rr circuit-breaker-options)
                      :execution-semaphore (r/make-counting-semaphore 10 semaphore-options)})]
    (is (map? collectors))
    (is (ifn? reporter))
    (is (map? (reporter)))
    (testing "Collectors used to construct resilience primitives"
      (is (map? @reporter))
      (is (ifn? o-reporter))
      (is (map? (o-reporter)))
      (is (map? @o-reporter)))
    (testing "Collectors used to collect metrics in resilience primitives"
      #_TODO)
    ))


(deftest tst-thread-pool-metrics
  )
