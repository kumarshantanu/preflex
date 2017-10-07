;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.util
  (:require
    [clojure.string :as str]
    [preflex.internal :as in]
    [preflex.type     :as t])
  (:import
    [java.text SimpleDateFormat]
    [java.util Calendar List Map UUID]
    [java.util.concurrent ExecutorService Future TimeoutException TimeUnit]))


;; ----- runtime -----


(defn java8-or-higher?
  "Return true if running in a Java (JVM) version 8 or higher, false otherwise."
  []
  (try
    (Class/forName "java.util.Optional")
    (catch ClassNotFoundException _
      false)))


;; ----- date, time & clock -----


(defn now-millis
  "Return number of milliseconds elapsed since epoch, or since specified start time."
  (^long []
    (System/currentTimeMillis))
  (^long [^long start-millis]
    (unchecked-subtract (System/currentTimeMillis) start-millis)))


(defn now-nanos
  "Return number of nanoseconds elapsed since epoch, or since specified start time."
  (^long []
    (System/nanoTime))
  (^long [^long start-nanos]
    (unchecked-subtract (System/nanoTime) start-nanos)))


(defn now-iso-8601
  "Return current ISO 8601 compliant date."
  ^String []
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssZ") (.getTime (Calendar/getInstance))))


(defn sleep-millis
  "Sleep for specified number of milliseconds."
  [^long millis]
  (try (Thread/sleep millis)
    (catch InterruptedException e
      (.interrupt (Thread/currentThread)))))


(defn parse-nanos
  "Parse duration string as nano seconds."
  [^String duration-str]
  (if-let [[_ strnum unit] (re-matches #"([0-9]+(?:.[0-9]+)?)([a-zA-Z]+)" duration-str)]
    (let [duration (Double/parseDouble strnum)]
      (case (str/lower-case unit)
        ;; nano seconds
        "ns"      (long duration)
        "nanos"   (long duration)
        ;; micro seconds
        "us"      (long (* duration 1000))
        "μs"      (long (* duration 1000))
        "micros"  (long (* duration 1000))
        ;; milli seconds
        "ms"      (long (* duration 1000 1000))
        "millis"  (long (* duration 1000 1000))
        ;; seconds
        "s"       (long (* duration 1000 1000 1000))
        "sec"     (long (* duration 1000 1000 1000))
        "secs"    (long (* duration 1000 1000 1000))
        "second"  (long (* duration 1000 1000 1000))
        "seconds" (long (* duration 1000 1000 1000))
        ;; minutes
        "m"       (long (* duration 1000 1000 1000 60))
        "min"     (long (* duration 1000 1000 1000 60))
        "mins"    (long (* duration 1000 1000 1000 60))
        "minute"  (long (* duration 1000 1000 1000 60))
        "minutes" (long (* duration 1000 1000 1000 60))
        ;; hours
        "h"       (long (* duration 1000 1000 1000 60 60))
        "hour"    (long (* duration 1000 1000 1000 60 60))
        "hours"   (long (* duration 1000 1000 1000 60 60))
        ;; days
        "d"       (long (* duration 1000 1000 1000 60 60 24))
        "day"     (long (* duration 1000 1000 1000 60 60 24))
        "days"    (long (* duration 1000 1000 1000 60 60 24))
        ; weeks
        "w"       (long (* duration 1000 1000 1000 60 60 24 7))
        "week"    (long (* duration 1000 1000 1000 60 60 24 7))
        "weeks"   (long (* duration 1000 1000 1000 60 60 24 7))
        (in/expected "suffix to be either of [ns, us, ms, s, m, h, d, w]" unit)))
    (in/expected "duration expressed as <NNNss> (e.g. 83ns, 103us, 239ms, 4s, 2m, 1h, 6d, 13w etc.)" duration-str)))


;; ----- Duration handling -----


(defn duration?
  "Return true if argument is a valid duration, false otherwise."
  [x]
  (and (satisfies? t/IDuration x)
    (t/duration? x)))


(defn resolve-time-unit
  "Resolve given time unit as java.util.concurrent.TimeUnit instance."
  ^TimeUnit [unit]
  (if (instance? TimeUnit unit)
    unit
    (case (if (string? unit)
            (-> unit
              str/trim
              str/lower-case
              keyword)
            unit)
      :days    TimeUnit/DAYS
      :hours   TimeUnit/HOURS
      :minutes TimeUnit/MINUTES
      :seconds TimeUnit/SECONDS
      :millis  TimeUnit/MILLISECONDS
      :micros  TimeUnit/MICROSECONDS
      :nanos   TimeUnit/NANOSECONDS
      (in/expected (str "a valid java.util.concurrent.TimeUnit instance or either of "
                     [:days :hours :minutes :seconds :millis :micros :nanos]) unit))))


(extend-protocol t/IDuration
  List
  (duration? [this] (and (= 2 (count this))
                      (integer? (first this))
                      (try (resolve-time-unit (second this)) true
                        (catch IllegalArgumentException e false))))
  (dur-time  [this] (long (first this)))
  (dur-unit  [this] (resolve-time-unit (second this)))
  (days      [this] (.toDays    (t/dur-unit this) (t/dur-time this)))
  (hours     [this] (.toHours   (t/dur-unit this) (t/dur-time this)))
  (minutes   [this] (.toMinutes (t/dur-unit this) (t/dur-time this)))
  (seconds   [this] (.toSeconds (t/dur-unit this) (t/dur-time this)))
  (millis    [this] (.toMillis  (t/dur-unit this) (t/dur-time this)))
  (micros    [this] (.toMicros  (t/dur-unit this) (t/dur-time this)))
  (nanos     [this] (.toNanos   (t/dur-unit this) (t/dur-time this)))
  Map
  (duration? [this] (if-let [t (or (get this :time) (get this "time"))]
                      (if-let [u (or (get this :unit) (get this "unit"))]
                        (and (integer? t)
                          (try (resolve-time-unit u) true
                            (catch IllegalArgumentException e false)))
                        false)
                      false))
  (dur-time  [this] (long (or (get this :time) (get this "time"))))
  (dur-unit  [this] (resolve-time-unit
                      (or (get this :unit) (get this "unit"))))
  (days      [this] (.toDays    (t/dur-unit this) (t/dur-time this)))
  (hours     [this] (.toHours   (t/dur-unit this) (t/dur-time this)))
  (minutes   [this] (.toMinutes (t/dur-unit this) (t/dur-time this)))
  (seconds   [this] (.toSeconds (t/dur-unit this) (t/dur-time this)))
  (millis    [this] (.toMillis  (t/dur-unit this) (t/dur-time this)))
  (micros    [this] (.toMicros  (t/dur-unit this) (t/dur-time this)))
  (nanos     [this] (.toNanos   (t/dur-unit this) (t/dur-time this)))
  String
  (duration? [this] (and
                      (try (t/dur-time this) true (catch NumberFormatException    e false))
                      (try (t/dur-unit this) true (catch IllegalArgumentException e false))))
  (dur-time  [this] (if-let [[_ strnum unit] (re-matches #"([0-9]+)([a-zA-Z]+)" this)]
                      (Long/parseLong strnum)))
  (dur-unit  [this] (if-let [[_ strnum unit] (re-matches #"([0-9]+)([a-zA-Z]+)" this)]
                      (resolve-time-unit unit)))
  (days      [this] (.toDays    (t/dur-unit this) (t/dur-time this)))
  (hours     [this] (.toHours   (t/dur-unit this) (t/dur-time this)))
  (minutes   [this] (.toMinutes (t/dur-unit this) (t/dur-time this)))
  (seconds   [this] (.toSeconds (t/dur-unit this) (t/dur-time this)))
  (millis    [this] (.toMillis  (t/dur-unit this) (t/dur-time this)))
  (micros    [this] (.toMicros  (t/dur-unit this) (t/dur-time this)))
  (nanos     [this] (.toNanos   (t/dur-unit this) (t/dur-time this))))


(defn make-duration
  "Create a preflex.type/IDuration instance from time and unit arguments."
  [time unit]
  (let [time (long time)
        unit (resolve-time-unit unit)]
    (reify t/IDuration
      (duration? [_] true)
      (dur-time  [_] time)
      (dur-unit  [_] unit)
      (days      [_] (.toDays    unit time))
      (hours     [_] (.toHours   unit time))
      (minutes   [_] (.toMinutes unit time))
      (seconds   [_] (.toSeconds unit time))
      (millis    [_] (.toMillis  unit time))
      (micros    [_] (.toMicros  unit time))
      (nanos     [_] (.toNanos   unit time)))))


(defn parse-duration
  "Parse duration string as duration object."
  [^String duration-str]
  (if-let [[_ strnum unit] (re-matches #"([0-9]+)([a-zA-Z]+)" duration-str)]
    (let [duration (Long/parseLong strnum)]
      (case (str/lower-case unit)
        ;; nano seconds
        "ns"      (make-duration duration :nanos)
        "nanos"   (make-duration duration :nanos)
        ;; micro seconds
        "us"      (make-duration duration :micros)
        "μs"      (make-duration duration :micros)
        "micros"  (make-duration duration :micros)
        ;; milli seconds
        "ms"      (make-duration duration :millis)
        "millis"  (make-duration duration :millis)
        ;; seconds
        "s"       (make-duration duration :seconds)
        "sec"     (make-duration duration :seconds)
        "secs"    (make-duration duration :seconds)
        "second"  (make-duration duration :seconds)
        "seconds" (make-duration duration :seconds)
        ;; minutes
        "m"       (make-duration duration :minutes)
        "min"     (make-duration duration :minutes)
        "mins"    (make-duration duration :minutes)
        "minute"  (make-duration duration :minutes)
        "minutes" (make-duration duration :minutes)
        ;; hours
        "h"       (make-duration duration :hours)
        "hour"    (make-duration duration :hours)
        "hours"   (make-duration duration :hours)
        ;; days
        "d"       (make-duration duration :days)
        "day"     (make-duration duration :days)
        "days"    (make-duration duration :days)
        ;; weeks - not supported by java.util.TimeUnit, so we multiply by 7 to express as days
        "w"       (make-duration (* 7 duration) :days)
        "week"    (make-duration (* 7 duration) :days)
        "weeks"   (make-duration (* 7 duration) :days)
        (in/expected (str "suffix to be either of "
                       "[ns/nanos, us/micros, ms/millis, s/seconds, m/minutes, h/hours, d/days, w/weeks]")
          unit)))
    (in/expected "duration expressed as <NNNss> (e.g. 83ns, 103us, 239ms, 4s, 2m, 1h, 6d, 13w etc.)" duration-str)))


;; ----- UUID -----


(defn uuid-str
  ([^UUID uuid]
    (.toString uuid))
  ([]
    (uuid-str (UUID/randomUUID))))


;; ----- concurrency -----


(defn get-thread-name
  "Rreturn current thread name."
  ^String []
  (.getName ^Thread (Thread/currentThread)))


(defn set-thread-name!
  "Set current thread name."
  [^String tname]
  (.setName ^Thread (Thread/currentThread) tname))


(defmacro with-thread-name
  "Execute body of code after setting current thread to the given name. Restore old name after the lexical scope is
  over. New threads launched from the body of code will not inherit the specified name."
  [thread-name & body]
  `(let [old-name# (get-thread-name)]
     (try
       (set-thread-name! ~thread-name)
       ~@body
       (finally
         (set-thread-name! old-name#)))))
