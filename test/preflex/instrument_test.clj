;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.instrument-test
  (:require
    [clojure.test :refer :all]
    [preflex.core       :as core]
    [preflex.instrument :as instru]
    [preflex.internal   :as in])
  (:import
    [java.util.concurrent ExecutorService]))


(defmacro with-active-thread-pool
  [[thread-pool-sym thread-pool] & body]
  (in/expected symbol? "a symbol to bind the thread-pool to" thread-pool-sym)
  `(let [~thread-pool-sym ~thread-pool]
     (try
       (do ~@body)
       (finally
         (.shutdownNow ~thread-pool-sym)))))


(deftest a-test
  (testing "dummy instrumentation"
    (with-active-thread-pool [^ExecutorService thread-pool (core/make-bounded-thread-pool 10 10)]
      (doseq [pool [thread-pool (:thread-pool thread-pool)]]
        (let [instru-pool (instru/instrument-thread-pool pool {})]
          (is (nil?
                @(.submit ^ExecutorService instru-pool ^Runnable #(do 10))))
          (is (= 10
                @(.submit ^ExecutorService instru-pool ^Callable #(do 10))))))))
  (testing ""
    (with-active-thread-pool [^ExecutorService thread-pool (core/make-bounded-thread-pool 10 10)]
      (let [instru-pool (instru/instrument-thread-pool thread-pool
                          {:on-callable-submit  {:before (fn [{:keys [callable] :as event}]
                                                           (instru/with-shared-context [context callable]
                                                             (vswap! context assoc :submit-time (System/nanoTime))))}
                           :on-runnable-submit  {:before (fn [{:keys [runnable] :as event}]
                                                           (instru/with-shared-context [context runnable]
                                                             (vswap! context assoc :submit-time (System/nanoTime))))}
                           :on-callable-execute {:before (fn [{:keys [callable] :as event}]
                                                           (instru/with-shared-context [context callable]
                                                             (vswap! context assoc :exec-time (System/nanoTime))))
                                                 :after  (fn [{:keys [callable] :as event}]
                                                           (instru/with-shared-context [context callable]
                                                             (let [{:keys [^long submit-time ^long exec-time]
                                                                    :as ctx-map} @context]
                                                               (printf "Queue-time: %dns, Exec-time-ns: %dns\n"
                                                                 (- exec-time submit-time)
                                                                 (- (System/nanoTime) exec-time))
                                                               (vswap! context assoc :end-time (System/nanoTime)))))}
                           :on-runnable-execute {:before (fn [{:keys [runnable] :as event}]
                                                           (instru/with-shared-context [context runnable]
                                                             (vswap! context assoc :exec-time (System/nanoTime))))
                                                 :after  (fn [{:keys [runnable] :as event}]
                                                           (instru/with-shared-context [context runnable]
                                                             (let [{:keys [^long submit-time ^long exec-time]
                                                                    :as ctx-map} @context]
                                                               (printf "Queue-time: %dns, Exec-time-ns: %dns\n"
                                                                 (- exec-time submit-time)
                                                                 (- (System/nanoTime) exec-time))
                                                               (vswap! context assoc :end-time (System/nanoTime)))))}
                           })]
        (is (nil?
              @(.submit ^ExecutorService instru-pool ^Runnable #(do 10))))
        (is (= 10
              @(.submit ^ExecutorService instru-pool ^Callable #(do 10))))))
   ))
