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
    [preflex.internal :as i]
    [preflex.type     :as t])
  (:import
    [clojure.lang Cons IFn]))


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
  (reify
    clojure.lang.IFn
    (applyTo [_ args] (do-either (apply f args)))
    (invoke  [_]                                           (do-either (f)))
    (invoke  [_ a]                                         (do-either (f a)))
    (invoke  [_ a b]                                       (do-either (f a b)))
    (invoke  [_ a b c]                                     (do-either (f a b c)))
    (invoke  [_ a b c d]                                   (do-either (f a b c d)))
    (invoke  [_ a b c d e]                                 (do-either (f a b c d e)))
    (invoke  [_ a b c d e f]                               (do-either (f a b c d e f)))
    (invoke  [_ a b c d e f g]                             (do-either (f a b c d e f g)))
    (invoke  [_ a b c d e f g h]                           (do-either (f a b c d e f g h)))
    (invoke  [_ a b c d e f g h i]                         (do-either (f a b c d e f g h i)))
    (invoke  [_ a b c d e f g h i j]                       (do-either (f a b c d e f g h i j)))
    (invoke  [_ a b c d e f g h i j k]                     (do-either (f a b c d e f g h i j k)))
    (invoke  [_ a b c d e f g h i j k l]                   (do-either (f a b c d e f g h i j k l)))
    (invoke  [_ a b c d e f g h i j k l m]                 (do-either (f a b c d e f g h i j k l m)))
    (invoke  [_ a b c d e f g h i j k l m n]               (do-either (f a b c d e f g h i j k l m n)))
    (invoke  [_ a b c d e f g h i j k l m n o]             (do-either (f a b c d e f g h i j k l m n o)))
    (invoke  [_ a b c d e f g h i j k l m n o p]           (do-either (f a b c d e f g h i j k l m n o p)))
    (invoke  [_ a b c d e f g h i j k l m n o p q]         (do-either (f a b c d e f g h i j k l m n o p q)))
    (invoke  [_ a b c d e f g h i j k l m n o p q r]       (do-either (f a b c d e f g h i j k l m n o p q r)))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s]     (do-either (f a b c d e f g h i j k l m n o p q r s)))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s t]   (do-either (f a b c d e f g h i j k l m n o p q r s t)))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s t u] (do-either (f a b c d e f g h i j k l m n o p q r s t u)))
    t/Invokable
    (apply-noarg     [_]        (do-either (f)))
    (apply-arguments [_ args]   (do-either (apply f args)))
    (success-result? [_ result] (not (instance? Failure result)))
    (success-error?  [_ error]  false)))


(defn bind
  "Given an either-result (success or failure) bind it with a function of respective type, i.e. success-f or failure-f.
  In other words, based on `result type` a call is made as follows:
  Either-result type  Function-called
  ------------------  ---------------
       success        (success-f success-result)
       failure        (failure-f failure-result)  ; returns failure-result as-is when failure-f is unspecified
  See:
    bind-deref
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


(defmacro bind-deref
  "Rewrite the arguments `result` and `exprs` as thread-first form using `bind`.
  For example, the expression below:
  (bind-> result
    [foo bar]
    baz)
  is rewritten as the following:
  (-> result
    (bind foo bar)
    (bind baz)
    deref-either)
  See:
    bind"
  [result & exprs]
  (let [forms (mapv (fn [x] (cond
                              (keyword? x)    `(bind ~x)
                              (symbol? x)     `(bind ~x)
                              (list? x)       `(bind ~x)
                              (instance?
                                Cons x)       `(bind ~x)
                              (and (vector? x)
                                (#{1 2}
                                  (count x))) `(bind ~@x)
                              :otherwise      (i/expected "vector of one or two forms" x)))
                exprs)]
    `(-> ~result
       ~@forms
       deref-either)))
