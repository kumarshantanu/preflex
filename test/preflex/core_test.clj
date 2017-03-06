;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.core-test
  (:require [clojure.test :refer :all]
            [preflex.core :as p]
            [preflex.impl :as im]
            [preflex.type :as t]
            [preflex.util :as u])
  (:import
    [java.util.concurrent
     ExecutorService
     RejectedExecutionException]
    [clojure.lang ExceptionInfo]))


(deftest test-thread-pool
  (let [core-size 10
        pool-size 20
        queue-len 30
        pool (p/make-bounded-thread-pool pool-size queue-len {:thread-pool-name "test-pool"
                                                              :core-thread-count core-size})
        idle #(u/sleep-millis 1000)
        sint (atom 1)]
    (is (= "test-pool" (name pool)))
    (testing "raw thread pool"
      (is (instance? ExecutorService pool) "created instance is a thread-pool")
      (p/future-call-via pool #(swap! sint inc))
      (idle) ; wait for some time hoping the task would be executed
      (is (= 2 @sint) "thread-pool task updates the accumulator")
      ;; submit enough tasks to fill up the thread pool
      (dotimes [i (+ pool-size queue-len)]
        (p/future-call-via pool idle))
      (is (thrown-with-msg? ExceptionInfo #"Thread-pool rejected execution"
            (p/future-call-via pool idle)) "any more task submission should be rejected")
      ;; wait for tasks to be drained
      (idle))
    (testing "with-async-executor"
      ;; normal execution
      (idle)  ; wait for stale tasks to be drained
      (idle)
      (is (= 30 (p/via-thread-pool pool {:task-timeout [1000 :millis]} #(+ 10 20)))
        "direct - instantenous async task")
      (is (= 30 ((p/wrap-thread-pool pool {:task-timeout [1000 :millis]} #(+ 10 20))))
        "wrapper - instantenous async task")
      (is (= 30 (p/with-thread-pool pool {:task-timeout [1000 :millis]} (+ 10 20)))
        "macro - instantenous async task")
      ;; timeout
      (idle)
      (is (thrown-with-msg? ExceptionInfo #"Operation timed out"
            (p/via-thread-pool pool {:task-timeout [100 :millis]} idle))
        "direct - timed out async task")
      (is (thrown-with-msg? ExceptionInfo #"Operation timed out"
            ((p/wrap-thread-pool pool {:task-timeout [100 :millis]} idle)))
        "wrapper - timed out async task")
      (is (thrown-with-msg? ExceptionInfo #"Operation timed out"
            (p/with-thread-pool pool {:task-timeout [100 :millis]} (idle)))
        "macro - timed out async task")
      ;; execution error
      (idle)
      (is (thrown-with-msg? ExceptionInfo #"Exception occurred"
            (p/via-thread-pool pool {:task-timeout [100 :millis]} #(throw (Exception. "foo"))))
        "direct - error async task")
      (is (thrown-with-msg? ExceptionInfo #"Exception occurred"
            ((p/wrap-thread-pool pool {:task-timeout [100 :millis]} #(throw (Exception. "foo")))))
        "wrapper - error async task")
      (is (thrown-with-msg? ExceptionInfo #"Exception occurred"
            (p/with-thread-pool pool {:task-timeout [100 :millis]} (throw (Exception. "foo"))))
        "macro - error async task")
      ;; submission rejection
      (idle)
      (dotimes [i (+ pool-size queue-len)]
        (p/future-call-via pool idle))
      (is (thrown-with-msg? ExceptionInfo #"Thread-pool rejected execution"
            (p/via-thread-pool pool {:task-timeout [100 :millis]} #(+ 10 20)))
        "direct - any more task submission should be rejected")
      (is (thrown-with-msg? ExceptionInfo #"Thread-pool rejected execution"
            ((p/wrap-thread-pool pool {:task-timeout [100 :millis]} #(+ 10 20))))
        "wrapper - any more task submission should be rejected")
      (is (thrown-with-msg? ExceptionInfo #"Thread-pool rejected execution"
            (p/with-thread-pool pool {:task-timeout [100 :millis]} (+ 10 20)))
        "macro - any more task submission should be rejected"))
    (.shutdown ^ExecutorService pool)))


(deftest test-semaphore
  (let [core-size 10
        pool-size 10
        queue-len 10
        thread-pool (p/make-bounded-thread-pool pool-size queue-len {:thread-pool-name "test-pool"
                                                                     :core-thread-count core-size})
        idle #(u/sleep-millis 1000)
        sem (p/make-counting-semaphore 10 {:semaphore-name "test-semaphore"})]
    (is (im/counting-semaphore? sem))
    (is (= "test-semaphore" (name sem)))
    (testing "Semaphore acquisition"
      (is (= 5 (p/via-semaphore sem #(+ 2 3))) "Semaphore allows acquisition when available")
      (is (= 5 ((p/wrap-semaphore sem #(+ 2 3)))) "Semaphore allows acquisition when available")
      (is (= 5 (p/with-semaphore sem {} (+ 2 3))) "Semaphore allows acquisition when available"))
    (testing "Semaphore rejection"
      ;; occupy available semaphores using async jobs
      (let [counter (atom 0)]
        (dotimes [_ 10]
          (p/future-call-via thread-pool (fn [] (p/via-semaphore sem #(do (swap! counter inc)
                                                                        (idle))))))
        ;; wait until all available semaphores are taken
        (while (< ^long @counter 10)
          (u/sleep-millis 10)))
      (is (thrown-with-msg? ExceptionInfo #"Semaphore rejected execution"
            (p/via-semaphore sem #(+ 2 3))) "Semaphore rejects acquisition when exhausted")
      (is (thrown-with-msg? ExceptionInfo #"Semaphore rejected execution"
            ((p/wrap-semaphore sem #(+ 2 3)))) "Semaphore rejects acquisition when exhausted")
      (is (thrown-with-msg? ExceptionInfo #"Semaphore rejected execution"
            (p/with-semaphore sem {} (+ 2 3))) "Semaphore rejects acquisition when exhausted"))
    (.shutdown ^ExecutorService thread-pool)))


(deftest test-serial-fault-detector
  (let [nn 10
        fd (p/make-serial-fault-detector nn)]
    (testing "Un-initialized"
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))
    (is (thrown? IllegalArgumentException (t/record! fd)) "Invalid metrics call")
    (testing "Incomplete failure count"
      (dotimes [_ (dec nn)]
        (t/record! fd false))
      (is (not (t/fault? fd)))
      (is (= {:count (dec nn)} (deref fd))))
    (testing "One success cancels all failure"
      (t/record! fd true)
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))
    (testing "Required failure count establishes fault"
      (dotimes [_ nn]
        (t/record! fd false))
      (is (t/fault? fd))
      (is (= {:count nn} (deref fd))))
    (testing "Continued failure upholds the failed status"
      (dotimes [_ nn]
        (t/record! fd false))
      (is (t/fault? fd))
      (is (= {:count (* 2 nn)} (deref fd))))
    (testing "Reinitialization wipes everything clean"
      (t/reinit! fd)
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))))


(deftest test-discrete-fault-detector
  (let [xx 10
        yy 100
        vv (volatile! 1488033798157)
        v+ (fn [^long n] (vswap! vv #(+ ^long % n)))
        fd (p/make-discrete-fault-detector xx yy {:now-finder #(deref vv)})]
    (testing "Un-initialized"
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))
    (is (thrown? IllegalArgumentException (t/record! fd)) "Invalid metrics call")
    (testing "Incomplete failure count"
      (dotimes [_ (dec xx)]
        (t/record! fd false))
      (t/record! fd true)  ; true soes not undermine failures
      (is (not (t/fault? fd)))
      (is (= {:count (dec xx)} (deref fd))))
    (testing "Elapsed time wipes failure count"
      (v+ yy)  ; hop to next time bucket
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))
    (testing "Complete failure count"
      (dotimes [_ xx]
        (t/record! fd false))
      (is (t/fault? fd))
      (is (= {:count xx} (deref fd))))
    (testing "Wipe out"
      (t/reinit! fd)
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))))


(deftest test-rolling-fault-detector
  (let [xx 10
        yy 100
        vv (volatile! 1488033798157)
        v+ (fn [^long n] (vswap! vv #(+ ^long % n)))
        fd (p/make-rolling-fault-detector xx yy {:bucket-interval 100
                                                 :deref-head? true
                                                 :event-id-fn #(deref vv)})]
    (testing "Un-initialized"
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))
    (is (thrown? IllegalArgumentException (t/record! fd)) "Invalid metrics call")
    (testing "Incomplete failure count"
      (dotimes [_ (dec xx)]
        (t/record! fd false))
      (t/record! fd true)  ; true soes not undermine failures
      (is (not (t/fault? fd)))
      (is (= {:count (dec xx)} (deref fd))))
    (testing "Elapsed time does not wipe failure count"
      (v+ yy)  ; hop to next time bucket
      (is (not (t/fault? fd)))
      (is (= {:count (dec xx)} (deref fd))))
    (testing "Complete failure count"
      (t/record! fd false)
      (is (t/fault? fd))
      (is (= {:count xx} (deref fd))))
    (testing "Wipe out"
      (t/reinit! fd)
      (is (not (t/fault? fd)))
      (is (= {:count 0} (deref fd))))))


(deftest test-half-open-retry-resolver
  (let [hh 100
        vv (volatile! 1488033798157)
        v+ (fn [^long n] (vswap! vv #(+ ^long % n)))
        rr (p/make-half-open-retry-resolver hh {:now-finder #(deref vv)
                                                :open-duration (* 2 hh)})]
    (testing "Un-initialized"
      (is (not (t/retry? rr))))
    (testing "Incomplete elapsing of open window"
      (v+ hh)
      (is (not (t/retry? rr))))
    (testing "After open window"
      (v+ hh)
      (is (t/retry? rr)))
    (testing "Incomplete elapsing of half-open window"
      (v+ (long (/ hh 2)))
      (is (not (t/retry? rr))))
    (testing "After half-open window"
      (v+ (long (/ hh 2)))
      (is (t/retry? rr)))
    (testing "Immediately after a retry candidate"
      (is (not (t/retry? rr))))
    (testing "Wipe out"
      (v+ hh)  ; elapse the window
      (t/reinit! rr)
      (is (not (t/retry? rr))))))


(deftest test-circuit-breaker
  (let [vfd (volatile! {:nfault 0
                        :fault? false})
        mfd (reify
              t/IMetricsRecorder   (record! [_] (throw (IllegalArgumentException. "This should never be called")))
              (record! [_ status?] nil)
              t/IReinitializable   (reinit! [_] (vreset! vfd {:nfault 0 :fault? false}))
              clojure.lang.Counted (count   [_] (:nfault @vfd))
              clojure.lang.IDeref  (deref   [_] {:nfault (:nfault @vfd)})
              t/IFaultDetector     (fault?  [_] (:fault? @vfd)))
        vrr (volatile! {:retry? false})
        mrr (reify
              clojure.lang.IDeref (deref   [_] @vrr)
              t/IReinitializable  (reinit! [_] (vreset! vrr {:retry? false}))
              t/IRetryResolver    (retry?  [_] (:retry? @vrr)))
        ccb (p/make-circuit-breaker
              mfd
              mrr
              {})]
    (testing "Initial state"
      (is (:state-connected? (deref ccb)) "Intial circuit-breaker state should be logical true")
      (is (= 5 (p/via-circuit-breaker ccb #(+ 2 3)))))
    (testing "Fault"
      (vswap! vfd assoc :fault? true :nfault 10)
      (is (thrown-with-msg? ExceptionInfo #"Circuit-breaker is open"
            (p/via-circuit-breaker ccb #(+ 2 3))) "circuit breaker open, so any more calls should be rejected"))
    (testing "Retry"
      (vswap! vrr assoc :retry? true)
      (is (thrown? Exception (p/via-circuit-breaker ccb #(throw (Exception. "test")))) "retry failure")
      (is (not (:state-connected? (deref ccb))) "retry failure should keep the circuit-breaker tripped")
      (is (= 5 (p/via-circuit-breaker ccb #(+ 2 3))) "retry success")
      (is (:state-connected? (deref ccb)) "retry success should lead to healed circuit-breaker"))))


(deftest test-circuit-breaker-integration
 (let [bi 100  ; bucket interval in millis
       fd (p/make-rolling-fault-detector
            10  ; X errors
            1000 ; in Y milliseconds
            {:bucket-interval bi})
       rr (p/make-half-open-retry-resolver 100)
       vc (volatile! {:trip-count 0
                      :connect-count 0})
       cb (p/make-circuit-breaker
            fd
            rr
            {:on-trip    (fn [x] (vswap! vc update :trip-count inc))
             :on-connect (fn [x] (vswap! vc update :connect-count inc))})
       cb-err (fn [] (p/via-circuit-breaker cb #(throw (Exception. "test"))))]
   (is (:state-connected? (deref cb)) "Intial circuit-breaker state should be logical true")
   (is (= 5 (p/via-circuit-breaker cb #(+ 2 3))))
   (testing "[trip->recover] Circuit-breaker open, followed by recovery"
     (dotimes [_ 10]  ; mix of alternating success and failure cases
       (is (= 5 (p/via-circuit-breaker cb #(+ 2 3))))
       (is (thrown? Exception (cb-err))))
     (u/sleep-millis (+ bi 20))
     (is (thrown-with-msg? ExceptionInfo #"Circuit-breaker is open"
           (p/via-circuit-breaker cb #(+ 2 3))) "circuit breaker open, so any more calls should be rejected")
     (is (= {:trip-count 1
             :connect-count 0} @vc))
     (u/sleep-millis bi)
     (is (= 5 (p/via-circuit-breaker cb #(+ 2 3))) "circuit breaker half-open, so one call should be allowed")
     (is (:state-connected? (deref cb)) "state should be connected due to the successful recovery test")
     (is (= 5 (p/via-circuit-breaker cb #(+ 2 3)))
       "last call was success, so circuit breaker should be closed and call should be allowed")
     (is (= {:trip-count 1
             :connect-count 1} @vc))
     (is (:state-connected? (deref cb)) "state should be connected due to the successful recovery test"))
   (testing "[trip->tripped] Circuit-breaker open, followed by no recovery"
     (vreset! vc {:trip-count 0
                  :connect-count 0})
     (t/mark! cb true)  ; set to connected state
     (dotimes [_ 10]
       (is (thrown? Exception (cb-err))))
     (u/sleep-millis bi)
     (is (thrown-with-msg? ExceptionInfo #"Circuit-breaker is open"
           (p/via-circuit-breaker cb #(+ 2 3))) "circuit breaker half-open, but must wait till window-close to allow")
     (is (= {:trip-count 1
             :connect-count 0} @vc))
     (u/sleep-millis bi)
     (is (thrown? Exception (cb-err)) "circuit breaker half-open, but must allow one call to test recovery")
     (is (thrown-with-msg? ExceptionInfo #"Circuit-breaker is open"
           (cb-err)) "circuit breaker still half-open, but recovery test failed, so must disallow during the window")
     (is (= {:trip-count 1
             :connect-count 0} @vc)))))


(deftest test-success-failure-tracker
  (let [status (volatile! nil)]
    (is (= 10 (p/via-success-failure-tracker (fn [?] (vreset! status ?)) #(+ 4 6))))
    (is @status)
    (is (thrown? Exception (p/via-success-failure-tracker (fn [?] (vreset! status ?)) #(throw (Exception. "test")))))
    (is (not @status))))


(deftest test-latency-tracker
  (let [result (volatile! [nil 0])]
    (is (= 10 (p/via-latency-tracker (fn [? lat] (vreset! result [? lat])) #(do (Thread/sleep 1) (+ 4 6)))))
    (is (= true (first @result)))
    (is (<= 1 (second @result)))
    (is (thrown? Exception (p/via-latency-tracker (fn [? lat] (vreset! result [? lat]))
                             #(do (Thread/sleep 2)
                                (throw (Exception. "test"))))))
    (is (= false (first @result)))
    (is (<= 2 (second @result)))))


(deftest test-fallback
  (is (= 10 (p/via-fallback nil #(+ 4 6))))
  (is (thrown? Exception (p/via-fallback nil #(throw (Exception. "test")))))
  (is (= 10 (p/via-fallback [#(do 10)] #(+ 4 6))))
  (is (= 30 (p/via-fallback [#(throw (Exception. "test")) #(+ 10 20)] #(throw (Exception. "test")))))
  (is (= 50 (p/via-fallback [#(+ 20 30) #(throw (Exception. "test"))] #(throw (Exception. "test"))))))
