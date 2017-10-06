;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.either
  "Success and Failure are the dual of each other with respect to an operation result. This namespace provides unified,
  standalone and composable mechanism to represent and process operation results as either success or failure.
  Reference:
    https://www.schoolofhaskell.com/school/starting-with-haskell/basics-of-haskell/10_Error_Handling
    https://youtu.be/3y7xzH8jB8A?t=1390"
  (:require
    [preflex.internal :as i]))


(defrecord Failure [result])


(defn failure
  "Represent given result as failure."
  ([result] (if (instance? Failure result)
              result
              (->Failure result)))
  ([]       (->Failure nil)))


(defn success
  "Represent given result as success. If the argument is a failure, then throw exception."
  ([result] (if (instance? Failure result)
              (throw (IllegalArgumentException. "Cannot convert failure into success."))
              result))
  ([]       nil))


(defn deref-either
  "Dereference an either-result to its raw result."
  [result]
  (if (instance? Failure result)
    (.-result ^Failure result)
    result))


(defmacro do-either
  "Evaluate given body of code and return either-result, i.e. preflex.either.Success on normal termination
  or preflex.either.Failure on exception. If the body of code returns either-result then return the same.
  See:
    failure
    success"
  [& body]
  `(try
     (let [result# (do ~@body)]
       (success result#))
     (catch Exception e#
       (failure e#))))


(defn either
  "Wrap given function f such that it returns an either-result based on the result of invoking f.
  See:
    do-either"
  [f]
  (fn [& args] (do-either (apply f args))))


(defn bind
  "Given an either-result (success or failure) bind it with a function of respective type, i.e. success-f or failure-f.
  In other words, based on `result type` a call is made as follows:
  Either-result type  Function-called
  ------------------  ---------------
       success        (success-f success-result)
       failure        (failure-f failure-result)  ; returns failure-result as-is when failure-f is unspecified
  See:
    bind->
    failure
    success
    deref-either
  Example:
    (-> (place-order)                   ; return placed-order details as either-result
      (bind check-inventory)            ; check inventory and return success or failure result
      (bind cancel-order process-order) ; cancel order on failed inventory check, process order on success
      (bind fulfil-order)               ; fulfil order if order is processed successfully
      ;; finally extract the result
      deref-either)"
  ([either-result success-f]
    (if (instance? Failure either-result)
      either-result
      (success-f either-result)))
  ([either-result failure-f success-f]
    (if (instance? Failure either-result)
      (failure-f (.-result ^Failure either-result))
      (success-f either-result))))


(defmacro bind->
  "Rewrite the arguments `result` and `exprs` as thread-first form using `bind`.
  For example, the expression below:
  (bind-> result
    (foo bar)
    baz
    (identity identity))
  is rewritten as the following:
  (-> result
    (bind foo bar)
    (bind baz)
    (bind identity identity))
  See:
    bind"
  [result & exprs]
  (let [forms (mapv (fn [x] (cond
                              (symbol? x)     `(bind ~x)
                              (and (list? x)
                                (#{1 2}
                                  (count x))) `(bind ~@x)
                              :otherwise      (i/expected "expression of one or two forms" x)))
                exprs)]
    `(-> ~result
       ~@forms)))
