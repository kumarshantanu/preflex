;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.metrics-test
  (:require
    [clojure.test :refer :all]
    [preflex.metrics :as m]
    [preflex.type    :as t]
    [preflex.util    :as u])
  (:import
    [preflex.rollingmetrics RollingMetrics]))


(deftest test-dummy
  (testing "default dummy"
    (let [d m/dummy-collector]
      (t/record! d)
      (t/record! d :foo)
      (is (zero? (count d)))
      (is (= {} (deref d)))
      (t/reinit! d)))
  (testing "custom dummy"
    (let [d (m/make-dummy-collector {:count-val 10
                                     :deref-val 20})]
      (t/record! d)
      (t/record! d :foo)
      (is (= 10 (count d)))
      (is (= 20 (deref d)))
      (t/reinit! d))))


(deftest test-integer-counter
  (let [c (m/make-integer-counter :foo)]
    (is (zero? (count c)))
    (is (= {:foo 0}
          (deref c)))
    (t/record! c)
    (t/record! c 5)
    (is (= 6 (count c)))
    (is (= {:foo 6}
          (deref c)))
    (t/reinit! c)
    (is (zero? (count c)))
    (is (= {:foo 0}
          (deref c)))))


(deftest test-boolean-collector
  (let [c (m/make-boolean-collector :foo)]
    (is (= {:foo false}
          (deref c)))
    (t/record! c)
    (is (= {:foo true}
          (deref c)))
    (t/record! c true)
    (is (= {:foo true}
          (deref c)))
    (t/record! c false)
    (is (= {:foo false}
          (deref c)))
    (t/reinit! c)
    (is (= {:foo false}
          (deref c)))))


(deftest test-boolean-counter
  (let [c (m/make-boolean-counter :foo :bar)]
    (is (= {:foo 0
            :bar 0}
          (deref c)))
    (t/record! c true)
    (t/record! c :logical-true)
    (t/record! c false)
    (t/record! c nil)  ; logical false
    (is (= {:foo 2
            :bar 2}
          (deref c)))
    (t/reinit! c)
    (is (= {:foo 0
            :bar 0}
          (deref c)))))


(deftest test-rolling-integer-counter
  (doseq [shard-count [0 1 8]]
    (testing (str "shard count " shard-count)
      (let [bi 100  ; bucket interval
            vlong (volatile! 1488033798157)
            long+ (fn ([^long x ^long y] (+ x y))
                    ([^long x ^long y ^long z] (+ x y z)))
            eifn #(deref vlong)
            c  (m/make-rolling-integer-counter :foo 11 {:bucket-interval bi    ; bucket size in millis
                                                        :buckets-key     :buckets
                                                        :deref-head?     false ; do not return head bucket
                                                        :event-id-fn     eifn
                                                        :shard-count     shard-count})]
        (testing "individual operations"
          (is (= {:foo 0 :buckets [0 0 0 0 0 0 0 0 0 0]} (deref c)))
          (t/record! c)
          (vswap! vlong long+ bi)
          (is (= {:foo 1 :buckets [1 0 0 0 0 0 0 0 0 0]} (deref c)))
          (t/record! c 8)
          (vswap! vlong long+ bi)
          (is (= {:foo 9 :buckets [8 1 0 0 0 0 0 0 0 0]} (deref c)))
          (t/reinit! c)
          (is (= {:foo 0 :buckets [0 0 0 0 0 0 0 0 0 0]} (deref c))))
        (testing "reinit"
          (t/reinit! c)
          (is (= {:foo 0 :buckets [0 0 0 0 0 0 0 0 0 0]} (deref c))))
        (testing "buckets"
          (t/reinit! c)
          (vswap! vlong long+ 10)
          (dotimes [_ 5]
            (dotimes [_ 10] (t/record! c))
            (vswap! vlong long+ bi))
          (is (= {:foo (* 10 5)
                  :buckets [10 10 10 10 10 0 0 0 0 0]}
                (deref c))))))))


(deftest test-rolling-boolean-counter
  (doseq [shard-count [0 1 8]]
    (testing (str "shard count " shard-count)
      (let [bi 100
            vlong (volatile! 1488033798157)
            long+ (fn ([^long x ^long y] (+ x y))
                    ([^long x ^long y ^long z] (+ x y z)))
            eifn #(deref vlong)
            c  (m/make-rolling-boolean-counter :true :false 11 {:bucket-interval bi    ; bucket size in milis
                                                                :buckets-truthy-key :buckets-truthy
                                                                :buckets-falsy-key  :buckets-falsy
                                                                :deref-head?     false ; do not return head bucket
                                                                :event-id-fn     eifn
                                                                :shard-count     shard-count})]
        (testing "individual operations"
          (is (= {:true 0
                  :false 0
                  :buckets-truthy [0 0 0 0 0 0 0 0 0 0]
                  :buckets-falsy [0 0 0 0 0 0 0 0 0 0]}
                (deref c)))
          (vswap! vlong long+ 10)  ; push a little inside into the bucket
          (is (thrown? UnsupportedOperationException (t/record! c)))
          (t/record! c true)
          (t/record! c :logical-true)
          (t/record! c false)
          (vswap! vlong long+ bi)
          (is (= {:true 2
                  :false 1
                  :buckets-truthy [2 0 0 0 0 0 0 0 0 0]
                  :buckets-falsy [1 0 0 0 0 0 0 0 0 0]}
                (deref c)) "after shift")
          (vswap! vlong long+ bi)
          (is (= {:true 2
                 :false 1
                 :buckets-truthy [0 2 0 0 0 0 0 0 0 0]
                 :buckets-falsy [0 1 0 0 0 0 0 0 0 0]}
               (deref c)) "after double shift"))))))


(deftest test-rolling-max-collector
  (doseq [shard-count [0 1 8]]
    (testing (str "shard count " shard-count)
      (let [bi 100
            vlong (volatile! 1488033798157)
            long+ (fn ([^long x ^long y] (+ x y))
                    ([^long x ^long y ^long z] (+ x y z)))
            eifn #(deref vlong)
            c (m/make-rolling-max-collector :foo 11 {:bucket-interval bi    ; bucket size in milis
                                                     :buckets-key     :buckets
                                                     :deref-head?     false ; do not return head bucket
                                                     :event-id-fn     eifn
                                                     :shard-count     shard-count})]
        (testing "init"
          (is (= {:foo 0
                  :buckets [0 0 0 0 0 0 0 0 0 0]}
                (deref c))))
        (testing "first bucket"
          (vswap! vlong long+ 10)  ; push a little inside into the bucket
          (is (thrown? UnsupportedOperationException (t/record! c)) "single arity is disallowed")
          (t/record! c 10)
          (t/record! c 20)
          (t/record! c 30)
          (vswap! vlong long+ bi)
          (is (= {:foo 30
                  :buckets [30 0 0 0 0 0 0 0 0 0]}
                (deref c)) "after shift"))
        (testing "first history bucket"
          (vswap! vlong long+ bi)
          (is (= {:foo 30
                  :buckets [0 30 0 0 0 0 0 0 0 0]}
                (deref c)) "after double shift"))
        (testing "third bucket"
          (t/record! c 11)
          (t/record! c 22)
          (t/record! c 33)
          (vswap! vlong long+ bi)  ; push to next bucket
          (is (= {:foo 33
                  :buckets [33 0 30 0 0 0 0 0 0 0 ]}
                (deref c)) "after 3 shifts"))
        (testing "reinit"
          (t/reinit! c)
          (is (= {:foo 0
                  :buckets [0 0 0 0 0 0 0 0 0 0]}
                (deref c))))))))


(deftest test-rolling-percentile-collector
  (doseq [shard-count [0 1 8]]
    (testing (str "shard count " shard-count)
      (let [bi 100
            vlong (volatile! 1488033798157)
            long+ (fn ([^long x ^long y] (+ x y))
                    ([^long x ^long y ^long z] (+ x y z)))
            eifn #(deref vlong)
            c (m/make-rolling-percentile-collector :foo [50 90 95 99 99.9] 11
                {:bucket-interval bi    ; bucket width in milis
                 :bucket-capacity 5     ; max 5 elements per bucket
                 :buckets-key     :buckets
                 :deref-head?     false ; do not return head bucket
                 :event-id-fn     eifn
                 :shard-count     shard-count})]
        (testing "init"
          (is (= {:foo nil
                  :buckets []}
                (deref c)) "no data collected because nothing recorded"))
        (testing "first bucket"
          (vswap! vlong long+ 10)  ; push a little inside into the bucket
          (is (thrown? UnsupportedOperationException (t/record! c)) "single arity is disallowed")
          (t/record! c 10)
          (t/record! c 20)
          (t/record! c 30)
          (vswap! vlong long+ bi)
          (is (= {:foo (t/map->SampleMetrics {:max (int 30)
                                              :mean (double 20)
                                              :median (double 20)
                                              :min (int 10)
                                              :percentiles {50 20
                                                            90 30
                                                            95 30
                                                            99 30
                                                            99.9 30}})
                  :buckets #{10 20 30}}
                (update (deref c) :buckets set)) "after shift"))
        (testing "first history bucket"
          (vswap! vlong long+ bi)
          (is (= {:foo (t/map->SampleMetrics {:max (int 30)
                                              :mean (double 20)
                                              :median (double 20)
                                              :min (int 10)
                                              :percentiles {50 20
                                                            90 30
                                                            95 30
                                                            99 30
                                                            99.9 30}})
                  :buckets #{10 20 30}}
                (update (deref c) :buckets set)) "after double shift"))
        (testing "third bucket"
          (t/record! c 11)
          (t/record! c 22)
          (t/record! c 33)
          (t/record! c 44)
          (t/record! c 55)
          (t/record! c 66)
          (vswap! vlong long+ bi)  ; push to next bucket
          (when (= 1 shard-count)  ; works for shard-count=1 only (because we need deterministic numbers)
            (is (= {:foo (t/map->SampleMetrics {:max (int 66)
                                                :mean 35.0
                                                :median 31.5
                                                :min (int 10)
                                                :percentiles {50 30
                                                              90 55
                                                              95 66
                                                              99 66
                                                              99.9 66}})
                    :buckets #{10 20 30 22 33 44 55 66}}
                  (update (deref c) :buckets set)) "after three shifts")))
        (testing "reinit"
          (t/reinit! c)
          (is (= {:foo nil
                  :buckets []}
                (deref c))))))))
