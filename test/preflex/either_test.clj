;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.either-test
  (:require
    [clojure.test :refer :all]
    [preflex.either :as either]))


(deftest test-basic
  (is (= :foo (either/deref-either (either/success :foo))) "deref success")
  (is (= :foo (either/deref-either (either/failure :foo))) "deref failure")
  (is (= :foo (either/deref-either (either/do-either :foo))) "deref do-either result"))


(deftest test-bind
  (is (= :foo (either/bind :foo identity)))
  (is (= :foo (-> (either/do-either :foo)
                (either/bind identity))))
  (is (thrown? IllegalArgumentException
        (-> (either/do-either (throw (IllegalStateException. "test error")))
          (either/bind #(throw (IllegalArgumentException. %)) identity))) "do-either throws exception")
  (is (= 1000 (-> :foo
                (either/bind either/failure)
                (either/bind {:foo 1000} vector))) "failure channel")
  (is (= [20] (-> :foo
                (either/bind {:foo 20})
                (either/bind {:foo 1000} vector))) "success channel")
  (is (= [210] (either/bind-deref 100
                 (fn [^long x] (* 2 x))
                 #(+ 10 ^long %)
                 (either/either vector))))
  (is (thrown? IllegalStateException
        (either/bind-deref (either/do-either (throw (IllegalStateException. "test error")))
          [#(throw %) identity]))))


(deftest test-bind->
  (is (= 4
        (either/bind-> :foo
          {:foo 1
           :bar 2}
          [(* 0) (+ 2)]
          inc)))
  (is (= 60
        (either/bind-> :foo
          either/failure
          [{:foo 10
            :bar 20} vector]
          (+ 50))))
  (is (= :foo
        (either/bind-> :foo
          either/failure
          [either/deref-either])) "1-element vector applies to both failure and success")
  (is (= :foo
        (either/bind-> :foo
          identity
          [either/deref-either])) "1-element vector applies to both failure and success"))


(deftest test-bind->>
  (is (= 2
        (either/bind->> :foo
          {:foo 1
           :bar 2}
          [(* 0) (vector 2)]
          first)))
  (is (= 1
        (either/bind->> :foo
          {:foo 1
           :bar 2}
          either/failure
          [(* 0) (vector 2)]
          inc)))
  (is (= :foo
        (either/bind->> :foo
          either/failure
          [either/deref-either])) "1-element vector applies to both failure and success")
  (is (= :foo
        (either/bind->> :foo
          identity
          [either/deref-either])) "1-element vector applies to both failure and success"))


(deftest test-bind-as->
  (is (= 1
        (either/bind-as-> :foo $
          ({:foo 1
            :bar 2} $)
          [(* 0 $) (vector $ 2)]
          (first $))))
  (is (= 30
        (either/bind-as-> :foo $
          ({:foo 1
            :bar 2} $)
          [(* 0 $) (vector $ 2)]
          30)))
  (is (= :foo
        (either/bind-as-> :foo $
          (either/failure $)
          [(either/deref-either $)])) "1-element vector applies to both failure and success")
  (is (= :foo
        (either/bind-as-> :foo $
          (identity $)
          [(either/deref-either $)])) "1-element vector applies to both failure and success"))
