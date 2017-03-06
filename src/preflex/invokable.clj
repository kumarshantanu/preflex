;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.invokable
  (:require
    [preflex.type :as t]))


(defn invokable?
  "Return true if argument is an invokable, false otherwise."
  [x]
  (satisfies? t/Invokable x))


(defn make-invokable
  "Turn given fn/invokable into an invokable with specified success and failure detector."
  ([f success-result? success-error?]
    (reify
      t/Invokable
      (apply-noarg     [_]        (t/apply-noarg f))
      (apply-arguments [_ args]   (t/apply-arguments f args))
      (success-result? [_ result] (boolean (success-result? result)))
      (success-error?  [_ error]  (boolean (success-error? error)))
      clojure.lang.IFn
      (applyTo [_ args] (t/apply-arguments f args))
      (invoke  [_] (t/apply-noarg f))
      (invoke  [_ a] (t/apply-arguments f [a]))
      (invoke  [_ a b] (t/apply-arguments f [a b]))
      (invoke  [_ a b c] (t/apply-arguments f [a b c]))
      (invoke  [_ a b c d] (t/apply-arguments f [a b c d]))
      (invoke  [_ a b c d e] (t/apply-arguments f [a b c d e]))
      (invoke  [_ a b c d e f] (t/apply-arguments f [a b c d e f]))
      (invoke  [_ a b c d e f g] (t/apply-arguments f [a b c d e f g]))
      (invoke  [_ a b c d e f g h] (t/apply-arguments f [a b c d e f g h]))
      (invoke  [_ a b c d e f g h i] (t/apply-arguments f [a b c d e f g h i]))
      (invoke  [_ a b c d e f g h i j] (t/apply-arguments f [a b c d e f g h i j]))
      (invoke  [_ a b c d e f g h i j k] (t/apply-arguments f [a b c d e f g h i j k]))
      (invoke  [_ a b c d e f g h i j k l] (t/apply-arguments f [a b c d e f g h i j k l]))
      (invoke  [_ a b c d e f g h i j k l m] (t/apply-arguments f [a b c d e f g h i j k l m]))
      (invoke  [_ a b c d e f g h i j k l m n] (t/apply-arguments f [a b c d e f g h i j k l m n]))
      (invoke  [_ a b c d e f g h i j k l m n o] (t/apply-arguments f [a b c d e f g h i j k l m n o]))
      (invoke  [_ a b c d e f g h i j k l m n o p] (t/apply-arguments f [a b c d e f g h i j k l m n o p]))
      (invoke  [_ a b c d e f g h i j k l m n o p q] (t/apply-arguments f [a b c d e f g h i j k l m n o p q]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r s]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s t] (t/apply-arguments f
                                                             [a b c d e f g h i j k l m n o p q r s t]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s t u] (t/apply-arguments f
                                                               (concat [a b c d e f g h i j k l m n o p q r s t] u)))))
  ([f]
    (reify
      t/Invokable
      (apply-noarg     [_]        (t/apply-noarg f))
      (apply-arguments [_ args]   (t/apply-arguments f args))
      (success-result? [_ result] (t/success-result? f result))
      (success-error?  [_ error]  (t/success-error? f error))
      clojure.lang.IFn
      (applyTo [_ args] (t/apply-arguments f args))
      (invoke  [_] (t/apply-noarg f))
      (invoke  [_ a] (t/apply-arguments f [a]))
      (invoke  [_ a b] (t/apply-arguments f [a b]))
      (invoke  [_ a b c] (t/apply-arguments f [a b c]))
      (invoke  [_ a b c d] (t/apply-arguments f [a b c d]))
      (invoke  [_ a b c d e] (t/apply-arguments f [a b c d e]))
      (invoke  [_ a b c d e f] (t/apply-arguments f [a b c d e f]))
      (invoke  [_ a b c d e f g] (t/apply-arguments f [a b c d e f g]))
      (invoke  [_ a b c d e f g h] (t/apply-arguments f [a b c d e f g h]))
      (invoke  [_ a b c d e f g h i] (t/apply-arguments f [a b c d e f g h i]))
      (invoke  [_ a b c d e f g h i j] (t/apply-arguments f [a b c d e f g h i j]))
      (invoke  [_ a b c d e f g h i j k] (t/apply-arguments f [a b c d e f g h i j k]))
      (invoke  [_ a b c d e f g h i j k l] (t/apply-arguments f [a b c d e f g h i j k l]))
      (invoke  [_ a b c d e f g h i j k l m] (t/apply-arguments f [a b c d e f g h i j k l m]))
      (invoke  [_ a b c d e f g h i j k l m n] (t/apply-arguments f [a b c d e f g h i j k l m n]))
      (invoke  [_ a b c d e f g h i j k l m n o] (t/apply-arguments f [a b c d e f g h i j k l m n o]))
      (invoke  [_ a b c d e f g h i j k l m n o p] (t/apply-arguments f [a b c d e f g h i j k l m n o p]))
      (invoke  [_ a b c d e f g h i j k l m n o p q] (t/apply-arguments f [a b c d e f g h i j k l m n o p q]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r s]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s t] (t/apply-arguments f
                                                             [a b c d e f g h i j k l m n o p q r s t]))
      (invoke  [_ a b c d e f g h i j k l m n o p q r s t u] (t/apply-arguments f
                                                               (concat [a b c d e f g h i j k l m n o p q r s t] u))))))


(defn partial-invokable
  "Partially apply specified arguments to given fn/invokable."
  [f args]
  (reify
    t/Invokable
    (apply-noarg     [this]        (t/apply-arguments f args))
    (apply-arguments [this more]   (t/apply-arguments f (concat args more)))
    (success-result? [this result] (t/success-result? f result))
    (success-error?  [this error]  (t/success-error?  f error))
    clojure.lang.IFn
    (applyTo [_ args] (t/apply-arguments f args))
    (invoke  [_] (t/apply-noarg f))
    (invoke  [_ a] (t/apply-arguments f [a]))
    (invoke  [_ a b] (t/apply-arguments f [a b]))
    (invoke  [_ a b c] (t/apply-arguments f [a b c]))
    (invoke  [_ a b c d] (t/apply-arguments f [a b c d]))
    (invoke  [_ a b c d e] (t/apply-arguments f [a b c d e]))
    (invoke  [_ a b c d e f] (t/apply-arguments f [a b c d e f]))
    (invoke  [_ a b c d e f g] (t/apply-arguments f [a b c d e f g]))
    (invoke  [_ a b c d e f g h] (t/apply-arguments f [a b c d e f g h]))
    (invoke  [_ a b c d e f g h i] (t/apply-arguments f [a b c d e f g h i]))
    (invoke  [_ a b c d e f g h i j] (t/apply-arguments f [a b c d e f g h i j]))
    (invoke  [_ a b c d e f g h i j k] (t/apply-arguments f [a b c d e f g h i j k]))
    (invoke  [_ a b c d e f g h i j k l] (t/apply-arguments f [a b c d e f g h i j k l]))
    (invoke  [_ a b c d e f g h i j k l m] (t/apply-arguments f [a b c d e f g h i j k l m]))
    (invoke  [_ a b c d e f g h i j k l m n] (t/apply-arguments f [a b c d e f g h i j k l m n]))
    (invoke  [_ a b c d e f g h i j k l m n o] (t/apply-arguments f [a b c d e f g h i j k l m n o]))
    (invoke  [_ a b c d e f g h i j k l m n o p] (t/apply-arguments f [a b c d e f g h i j k l m n o p]))
    (invoke  [_ a b c d e f g h i j k l m n o p q] (t/apply-arguments f [a b c d e f g h i j k l m n o p q]))
    (invoke  [_ a b c d e f g h i j k l m n o p q r] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r]))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s] (t/apply-arguments f [a b c d e f g h i j k l m n o p q r s]))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s t] (t/apply-arguments f
                                                           [a b c d e f g h i j k l m n o p q r s t]))
    (invoke  [_ a b c d e f g h i j k l m n o p q r s t u] (t/apply-arguments f
                                                             (concat [a b c d e f g h i j k l m n o p q r s t] u)))))


(defmacro invoke-task
  "Given an invokable (and args), execute it and return either [result nil success?] or [nil error success?]."
  ([klass f]
    `(let [g# ~f]
       (try
         (let [result# (t/apply-noarg g#)]
           [result# nil (t/success-result? g# result#)])
         (catch ~klass error#
           [nil error# (t/success-error? g# error#)]))))
  ([klass f args]
    `(invoke-task ~klass (partial-invokable ~f ~args))))
