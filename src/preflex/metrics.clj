;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.metrics
  (:require
    [preflex.type :as t]
    [preflex.util :as u])
  (:import
    [java.util                   Arrays]
    [java.util.concurrent        ArrayBlockingQueue BlockingQueue ExecutionException Future Semaphore
                                 ThreadPoolExecutor ThreadLocalRandom TimeUnit RejectedExecutionException
                                 TimeoutException]
    [java.util.concurrent.atomic AtomicBoolean AtomicLong]
    [preflex.rollingmetrics      IRollingCount IRollingRecord RollingMetrics]
    [preflex.util                Stats]))


;; ----- dummy collectors -----


(defn make-dummy-collector
  "Create dummy metrics collector with specified mock return values."
  ([]
    (make-dummy-collector {}))
  ([{:keys [count-val deref-val]
     :or {count-val 0
          deref-val {}}}]
    (reify
      t/IMetricsRecorder   (record!       [_])
                           (record! [_ value])
      clojure.lang.Counted (count         [_] count-val)
      clojure.lang.IDeref  (deref         [_] deref-val)
      t/IReinitializable   (reinit!       [_]))))


(def dummy-collector (make-dummy-collector))


;; ----- generic metrics recorder -----


(defn metrics-recorder?
  [x]
  (satisfies? t/IMetricsRecorder x))


(defn make-union-collector
  "Create a super collector from one or more collectors, so that invoking it propagates request to all collectors.
  Calling `deref` on the union recorder simply merges the `deref` results from all constituent recorders. Beware of
  other protocols implemented in the original recorders being unavailable in the union recorder."
  [collectors]
  (reify
    t/IMetricsRecorder  (record!       [_] (doseq [each collectors] (t/record! each)))
                        (record! [_ value] (doseq [each collectors] (t/record! each value)))
    t/IReinitializable  (reinit!       [_] (doseq [each collectors] (t/reinit! each)))
    clojure.lang.IDeref (deref         [_] (->> collectors
                                             (map deref)
                                             (reduce merge {})))))


(defn make-sharding-collector
  "Create a unified collector that randomly distributes `record!` calls across given metrics collectors in a random
  sharding manner. If collectors >= CPU cores, any concurrent `record!` operations may be mostly contention free.
  NOTE: Works only when `record!` operations on all collectors are associative and commutative, e.g. shared counters."
  [f collectors]
  (let [n (count collectors)
        find-shard (fn ^long [] (let [^ThreadLocalRandom tlr (ThreadLocalRandom/current)]
                                  (.nextInt tlr 0 n)))]
    (reify
      t/IMetricsRecorder   (record!       [_] (t/record! (get collectors (find-shard))))
                           (record! [_ value] (t/record! (get collectors (find-shard) value)))
      t/IReinitializable   (reinit!       [_] (doseq [each collectors] (t/reinit! each)))
      clojure.lang.Counted (count         [_] (-> collectors
                                                (map count)
                                                (reduce +)))
      clojure.lang.IDeref  (deref         [_] (reduce f collectors)))))


(defn resolve-shard-count
  "Resolve shard count. Arguments `:detect` and `:detect-java7` return a value up to 128 based on the number of
  available CPU cores."
  ^long [shard-count]
  (let [detected (* (max (.availableProcessors (Runtime/getRuntime)) 64) 2)] ; max 128 shards
    (case shard-count
      :detect       detected
      :detect-java7 (if (u/java8-or-higher?)  ; shard only when version lower than Java 8
                      1
                      detected)
      nil 1
      (int shard-count))))


;; ----- single-state recorders -----


(def default-counter-options {:shard-count :detect-java7})


(defn make-integer-counter
  "Create an atomic, blocking long integer counter.
  Required arguments:
    deref-key - the reporting keyword to associate the value with upon `deref`
  Optional arguments:
    :initial-value (long) - Value to initialize the counter with; default 0.
    :shard-count   (int)  - Number of shards to create to reduce contention; auto-detect by default.
    :counter-shard-count  - overrides :shard-count"
  ([deref-key]
    (make-integer-counter deref-key default-counter-options))
  ([deref-key {:keys [^long initial-value counter-shard-count shard-count]
               :or {initial-value 0}}]
    (let [n (resolve-shard-count (or counter-shard-count shard-count))
          f (fn []
              (let [^AtomicLong counter (AtomicLong. initial-value)]
                (reify
                  t/IMetricsRecorder   (record!       [_] (.incrementAndGet counter))
                                       (record! [_ value] (.addAndGet counter value))
                  t/IReinitializable   (reinit!       [_] (.set counter initial-value))
                  clojure.lang.Counted (count         [_] (.get counter))
                  clojure.lang.IDeref  (deref         [_] {deref-key (.get counter)}))))]
      (if (> n 1)
        (make-sharding-collector + (vec (repeatedly n f)))
        (f)))))


(defn make-boolean-collector
  "Create an atomic, blocking boolean value store."
  ([deref-key]
    (make-boolean-collector deref-key false))
  ([deref-key initial-value]
    (let [^AtomicBoolean store (AtomicBoolean. (boolean initial-value))]
      (reify
        t/IMetricsRecorder  (record!       [_] (.set store (not (.get store))))
                            (record! [_ value] (.set store (boolean value)))
        t/IReinitializable  (reinit!       [_] (.set store (boolean initial-value)))
        clojure.lang.IDeref (deref         [_] {deref-key (.get store)})))))


(defn make-boolean-counter
  "Create a metrics collector that stores the count of truthy and falsy values separately, and on `deref` returns a map
  as follows:
  {deref-truthy-key truthy-count
   deref-falsy-key  falsy-count}"
  ([deref-truthy-key deref-falsy-key]
    (make-boolean-counter deref-truthy-key deref-falsy-key default-counter-options))
  ([deref-truthy-key deref-falsy-key options]
    (let [truthy-counter (make-integer-counter deref-truthy-key options)
          falsy-counter  (make-integer-counter deref-falsy-key options)]
      (reify
        t/IMetricsRecorder  (record!       [_] (throw (UnsupportedOperationException.
                                                        "Arity-0 is not allowed, must pass value argument")))
                            (record! [_ value] (t/record! (if value truthy-counter falsy-counter)))
        t/IReinitializable  (reinit!       [_] (do
                                                 (t/reinit! truthy-counter)
                                                 (t/reinit! falsy-counter)))
        clojure.lang.IDeref (deref         [_] {deref-truthy-key (count truthy-counter)
                                                deref-falsy-key  (count falsy-counter)})))))


;; ----- rolling count and percentiles -----


(defn make-rolling-integer-counter
  "Create bucketed rolling count collector. Optional args default to making a per-second counter.
  Arguments:
    deref-key    (keyword) key to associate the count with (upon deref)
    bucket-count (integer) number of buckets in the buffer
  Options:
    :bucket-interval (integer)  diff between min and max possible event IDs in any bucket (default 1000 = 1 second)
    :buckets-key     (keyword)  key to associate the buckets data in the deref result (nil omits bucket data)
    :deref-head?     (boolean)  query even the current/head bucket during deref? (false by default)
    :event-id-fn     (function) no-arg fn to return latest event ID (returns current time in milliseconds by default)
    :shard-count     (integer)  number of shards to split write-load across"
  ([deref-key ^long bucket-count]
    (make-rolling-integer-counter deref-key bucket-count {}))
  ([deref-key ^long bucket-count {:keys [^long bucket-interval
                                         buckets-key
                                         deref-head?
                                         event-id-fn
                                         shard-count]
                                  :or {bucket-interval 1000  ; 1 second
                                       deref-head?     false ; do not return current bucket
                                       event-id-fn     u/now-millis
                                       shard-count     0}}]
    (let [^IRollingRecord rolling-sum (RollingMetrics/createRollingSum
                                        bucket-count bucket-interval event-id-fn shard-count)
          find-elems (if deref-head?
                       (fn ^longs [] (.getAllElements rolling-sum))
                       (fn ^longs [] (.getPreviousElements rolling-sum)))]
      (reify
        t/IMetricsRecorder   (record!   [_] (.record rolling-sum 1))
                             (record! [_ v] (.record rolling-sum v))
        t/IReinitializable   (reinit!   [_] (.reset  rolling-sum))
        clojure.lang.Counted (count     [_] (Stats/sum (find-elems)))
        clojure.lang.IDeref  (deref     [_] (let [^longs elems (find-elems)
                                                  deref-result {deref-key (Stats/sum elems)}]
                                              (if buckets-key
                                                (assoc deref-result buckets-key (vec elems))
                                                deref-result)))))))


(defn make-rolling-boolean-counter
  "Create bucketed rolling count collector for truthy and falsy values respectively. Optional args default to making a
  per-second counter.
  Arguments:
    deref-truthy-key (keyword) key to associate the truthy count with (upon deref)
    deref-falsy-key  (keyword) key to associate the falsy count with (upon deref)
    bucket-count     (integer) number of buckets in the buffer
  Options:
    :bucket-interval (integer)  diff between min and max possible event IDs in any bucket (default 1000 = 1 second)
    :buckets-truthy-key (keyword)  key to associate the truthy buckets data in the deref result (nil omits bucket data)
    :buckets-falsy-key  (keyword)  key to associate the falsy buckets data in the deref result (nil omits bucket data)
    :deref-head?     (boolean)  query even the current bucket during deref? (false by default)
    :event-id-fn     (function) no-arg fn to return latest event ID (returns current time in milliseconds by default)
    :shard-count     (integer)  number of shards to split write-load across"
  ([deref-truthy-key deref-falsy-key ^long bucket-count]
    (make-rolling-boolean-counter deref-truthy-key deref-falsy-key bucket-count {}))
  ([deref-truthy-key deref-falsy-key ^long bucket-count
    {:keys [^long bucket-interval
            buckets-truthy-key
            buckets-falsy-key
            deref-head?
            event-id-fn
            shard-count]
     :or {bucket-interval 1000  ; 1 second
          deref-head?     false ; do not return current bucket
          event-id-fn     u/now-millis
          shard-count     0}}]
    (let [^IRollingRecord rolling-truthy-sum (RollingMetrics/createRollingSum
                                               bucket-count bucket-interval event-id-fn shard-count)
          ^IRollingRecord rolling-falsy-sum  (RollingMetrics/createRollingSum
                                               bucket-count bucket-interval event-id-fn shard-count)
          find-elems (if deref-head?
                       (fn [] {deref-truthy-key (.getAllElements rolling-truthy-sum)
                               deref-falsy-key  (.getAllElements rolling-falsy-sum)})
                       (fn [] {deref-truthy-key (.getPreviousElements rolling-truthy-sum)
                               deref-falsy-key  (.getPreviousElements rolling-falsy-sum)}))
          derefer    (fn [elems]
                       {deref-truthy-key (Stats/sum ^longs (get elems deref-truthy-key))
                        deref-falsy-key  (Stats/sum ^longs (get elems deref-falsy-key))})]
      (reify
        t/IMetricsRecorder  (record!   [_] (throw (UnsupportedOperationException.
                                                    "Arity-0 is not allowed, must pass value argument")))
                            (record! [_ v] (.record ^IRollingRecord (if v rolling-truthy-sum rolling-falsy-sum) 1))
        t/IReinitializable  (reinit!   [_] (do
                                             (.reset rolling-truthy-sum)
                                             (.reset rolling-falsy-sum)))
        clojure.lang.IDeref (deref     [_] (let [elems (find-elems)
                                                 assoc-when (fn [m k j] (if k (assoc m k (vec (get elems j))) m))]
                                             (-> (derefer elems)
                                               (assoc-when buckets-truthy-key deref-truthy-key)
                                               (assoc-when buckets-falsy-key  deref-falsy-key))))))))


(defn make-rolling-max-collector
  "Create bucketed rolling max-value collector. Optional args default to making a per-second counter.
  Arguments:
    deref-key    (keyword) key to associate the count with (upon deref)
    bucket-count (integer) number of buckets in the buffer
  Options:
    :bucket-interval (integer)  diff between min and max possible event IDs in any bucket (default 1000 = 1 second)
    :buckets-key     (keyword)  key to associate the buckets data in the deref result (nil omits bucket data)
    :deref-head?     (boolean)  query even the current bucket during deref? (false by default)
    :event-id-fn     (function) no-arg fn to return latest event ID (returns current time in milliseconds by default)
    :shard-count     (integer)  number of shards to split write-load across"
  ([deref-key ^long bucket-count {:keys [^long bucket-interval
                                         buckets-key
                                         deref-head?
                                         event-id-fn
                                         shard-count]
                                  :or {bucket-interval 1000  ; 1 second
                                       deref-head?     false ; do not return current bucket
                                       event-id-fn     u/now-millis
                                       shard-count     0}}]
    (let [^IRollingRecord rolling-max (RollingMetrics/createRollingMax
                                        bucket-count bucket-interval event-id-fn shard-count)]
      (reify
        t/IMetricsRecorder  (record!   [_] (throw (UnsupportedOperationException.
                                                    "Arity-0 is not allowed, must pass value argument")))
                            (record! [_ v] (.record rolling-max v))
        t/IReinitializable  (reinit!   [_] (.reset  rolling-max))
        clojure.lang.IDeref (deref     [_] (let [^ints elements (if deref-head?
                                                                  (.getAllElements rolling-max)
                                                                  (.getPreviousElements rolling-max))
                                                 assoc-when (fn [m k] (if k
                                                                        (assoc m k (vec elements))
                                                                        m))]
                                             (-> {deref-key (apply max elements)}
                                               (assoc-when buckets-key))))))))


(defn make-rolling-percentile-collector
  "Create bucketed rolling percentile collector. Optional args default to making a per-second counter.
  Arguments:
    deref-key    (keyword) key to associate the count with (upon deref)
    percentiles  (seqable) list of percentiles to calculate
    bucket-count (integer) number of buckets in the buffer
  Options:
    :bucket-interval (integer)  diff between min and max possible event IDs in any bucket (default 1000 = 1 second)
    :bucket-capacity (integer) max number of values in every bucket
    :buckets-key     (keyword)  key to associate the buckets data in the deref result (nil omits bucket data)
    :deref-head?     (boolean)  query the current bucket during deref? (false by default)
    :event-id-fn     (function) no-arg fn to return latest event ID (returns current time in milliseconds by default)
    :shard-count     (integer)  number of shards to split write-load across"
  ([deref-key percentiles ^long bucket-count]
    (make-rolling-percentile-collector deref-key percentiles bucket-count {}))
  ([deref-key percentiles ^long bucket-count
    {:keys [^long bucket-interval
            ^long bucket-capacity
            buckets-key
            deref-head?
            event-id-fn
            shard-count]
     :or {bucket-interval 1000  ; 1 second
          bucket-capacity 128   ; max 128 values in every bucket
          deref-head?     false ; do not return current bucket
          event-id-fn     u/now-millis
          shard-count     0}}]
    (let [^IRollingRecord rolling-store (RollingMetrics/createRollingStore
                                          bucket-count bucket-interval bucket-capacity event-id-fn shard-count)]
      (reify
        t/IMetricsRecorder  (record!   [_] (throw (UnsupportedOperationException.
                                                    "Arity-0 is not allowed, must pass value argument")))
                            (record! [_ v] (.record rolling-store v))
        t/IReinitializable  (reinit!   [_] (.reset  rolling-store))
        clojure.lang.IDeref (deref     [_] (let [^longs elements (if deref-head?
                                                                   (.getAllElements rolling-store)
                                                                   (.getPreviousElements rolling-store))
                                                 assoc-when (fn [m k] (if k
                                                                        (assoc m k (vec elements))
                                                                        m))]
                                             (Arrays/sort elements)
                                             (-> {deref-key (when (pos? (alength elements))
                                                              (t/->SampleMetrics
                                                                (Stats/last    elements) ; max
                                                                (Stats/average elements) ; mean
                                                                (Stats/median  elements) ; median
                                                                (Stats/first   elements) ; min
                                                                ;; percentiles
                                                                (->> percentiles
                                                                  (map #(Stats/percentile elements %))
                                                                  (zipmap percentiles))))}
                                               (assoc-when buckets-key))))))))
