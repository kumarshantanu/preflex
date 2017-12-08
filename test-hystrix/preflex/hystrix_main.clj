;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.hystrix-main
  (:require
    [cheshire.core      :as json]
    [org.httpkit.server :as hks]
    [ring-sse-middleware.core             :as ssec]
    [ring-sse-middleware.wrapper          :as ssew]
    [ring-sse-middleware.adapter.http-kit :as sseh]
    [preflex.resilient         :as r]
    [preflex.resilient.hystrix :as h])
  (:gen-class))


(defn primary []
  (when (< 4 ^long (rand-int 10))
    (throw (Exception. "Random exception")))
  (try (Thread/sleep (rand-int 10))
    (catch InterruptedException _))
  :foo)


(defn secondary [] :foo)


(defn setup
  []
  (let [{:keys [;; trackers
                latency-tracker
                success-failure-tracker
                ;; options
                success-failure-options
                circuit-breaker-options
                semaphore-options
                thread-pool-options
                metrics-collectors]
         :as collectors} (h/make-command-metrics-collectors)
        tp-collectors (:metrics-collectors (h/make-thread-pool-metrics-collectors))
        reporter (h/make-command-metrics-reporter metrics-collectors)
        fd (r/make-rolling-fault-detector 20 [10000 :millis])
        rr (r/make-half-open-retry-resolver [5 :seconds])
        circuit-breaker     (r/make-circuit-breaker fd rr circuit-breaker-options)
        execution-semaphore (r/make-counting-semaphore 10 semaphore-options)
        thread-pool (r/make-bounded-thread-pool 10 20)
        cm-reporter (h/make-command-metrics-reporter metrics-collectors
                      {:circuit-breaker     circuit-breaker
                       :execution-semaphore execution-semaphore})
        tp-reporter (h/make-thread-pool-metrics-reporter tp-collectors
                      (:thread-pool thread-pool))
        command-metrics-source (h/make-hystrix-command-metrics-source "sample-command" cm-reporter cm-reporter)
        th-pool-metrics-source (h/make-hystrix-thread-pool-metrics-source "sample-thread-pool" tp-reporter)
        command (->> primary
                  (r/wrap-thread-pool thread-pool thread-pool-options)
                  (r/wrap-semaphore execution-semaphore semaphore-options)
                  (r/wrap-circuit-breaker circuit-breaker circuit-breaker-options)
                  (r/wrap-success-failure-tracker success-failure-tracker success-failure-options)
                  (r/wrap-latency-tracker latency-tracker {})
                  (r/wrap-fallback [secondary]))]
    (-> (fn [request]
          (command)
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    "hello HTTP!"})
      (ssec/streaming-middleware sseh/generate-stream {:request-matcher (partial ssec/uri-match "/hystrix.stream")
                                                       :chunk-generator (-> (fn [_] (str
                                                                                      (json/generate-string
                                                                                        (command-metrics-source))
                                                                                      \newline
                                                                                      \newline
                                                                                      (json/generate-string
                                                                                        (th-pool-metrics-source))) )
                                                                          (ssew/wrap-delay 1000)
                                                                          ssew/wrap-sse-event
                                                                          ssew/wrap-pst)}))))


(defn -main
  [& args]
  (-> (setup)  ; ring handler
    (hks/run-server {:port 3000}))
  (println "HTTP Kit server started on http://localhost:3000")
  (println "Streaming Hystrix metrics at URL http://localhost:3000/hystrix.stream"))
