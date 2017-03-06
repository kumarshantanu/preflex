;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.internal
  (:require
    [preflex.type :as t])
  (:import
    [java.util.concurrent Future TimeoutException TimeUnit]))


(def nop (constantly nil))


(defn expected
  "Throw illegal input exception citing `expectation` and what was `found` did not match. Optionally accept a predicate
  fn to test `found` before throwing the exception."
  ([expectation found]
    (throw (IllegalArgumentException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


(defn as-str
  [x]
  (if (instance? clojure.lang.Named x)
    (if-let [prefix (namespace x)]
      (str prefix \/ (name x))
      (name x))
    (str x)))


(defmacro maybe
  "Given exception class names return the result of evaluating body of code as either [result nil] or [nil exception].
  Throw exception if the body of code throws an exception not covered by specified exception class names."
  [klasses & body]
  (expected seq "one or more exception classes" klasses)
  (let [catches (map (fn [klass]
                       `(catch ~klass e# [nil e#]))
                  klasses)]
    `(try [(do ~@body) nil]
       ~@catches)))


(defmacro maybe-call
  "Return vector [result error success?] upon invoking the task."
  [klasses f]
  (let [g-sym   (gensym)
        catches (map (fn [klass]
                       `(catch ~klass e# [nil e# (t/success-error? ~g-sym e#)]))
                  klasses)]
    `(let [~g-sym ~f]
       (try (let [result# (t/apply-noarg ~g-sym)]
             [result# nil (t/success-result? ~g-sym result#)])
        ~@catches))))


(defn deref-future
  "Deref a java.util.concurrent.Future object."
  ([^Future future {:keys [on-deref-error]
                    :or {on-deref-error nop}}]
     (try
       (.get future)
       (catch Exception e
         (on-deref-error e)
         (throw e))))
  ([^Future future timeout-ms timeout-val {:keys [on-deref-error
                                                  on-deref-timeout]
                                           :or {on-deref-error   nop
                                                on-deref-timeout nop}}]
     (try
       (.get future timeout-ms TimeUnit/MILLISECONDS)
       (catch TimeoutException e
         (on-deref-timeout e)
         timeout-val)
       (catch Exception e
         (on-deref-error e)
         (throw e)))))
