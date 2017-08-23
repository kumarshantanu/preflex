;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.internal
  (:require
    [clojure.reflect :as r]
    [preflex.type    :as t])
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
  ([^Future future]
    (deref-future future {}))
  ([^Future future {:keys [on-deref-error]
                    :or {on-deref-error nop}}]
     (try
       (.get future)
       (catch Exception e
         (on-deref-error e)
         (throw e))))
  ([^Future future timeout-ms timeout-val]
    (deref-future future timeout-ms timeout-val {}))
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


;; ===== interfaces and classes =====


(defn public-method?
  "Return true if method spec belongs to a public method, false otherwise."
  [method-spec]
  (and
    ;; truthy result implies this is a method
    (:return-type method-spec)
    ;; ensure not a constructor name
    (neg? (.indexOf (str (:name method-spec)) "."))
    ;; public methods only
    (get-in method-spec [:flags :public])))


(defn type-methods
  "Given a collection of class and interface symbols, discover methods using reflection and return the ones to be
  implemented using Clojure. The key :arity (value: integer) is added to each spec to indicate the method arity.
  :name            (symbol)        name
  :type            (symbol)        class name or primitive data type for fields, nil for methods
  :return-type     (symbol)        class name or primitive data type for methods, nil for fields
  :declaring-class (symbol)        class that declared the method
  :parameter-types (symbol vector) classes representing the argument types
  :exception-types (symbol vector) classes representing the exception types
  :flags           (keyword set)   a set containing any or more of
                      :public, :private, :static, :bridge, :synthetic, :static, :varargs
  :arity           (long)          arity of the method"
  [pred class-and-interfaces]
  (->> class-and-interfaces
    (map str)
    (map #(if-let [klass (Class/forName ^String %)]
            klass
            (expected "a class" %)))
    (map r/type-reflect)
    (map :members)
    (mapcat #(filter pred %))
    (sort-by :name)
    (group-by :name)  ; group overloaded methods together - multiple signatures
    vals
    (sort-by #(:name (first %)))
    (map (fn [specs]
           (->> specs
             (sort-by #(count (:parameter-types %)))
             (group-by #(count (:parameter-types %))) ; group type-overloaded methods together - unsupported in Clj
             (map (fn [[param-count same-arity-specs]]
                    (assoc (first same-arity-specs)
                      :arity param-count)))
             (sort-by :arity))))))
