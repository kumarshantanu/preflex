# Introduction to preflex

<!---
TODO: write [great documentation](http://jacobian.org/writing/what-to-write/)
-->

Preflex helps your application behave gracefully in the presence of faults and overload. It improves the resilience
and observability of your application using instrumentation, metrics and safety abstractions.

Rest of the document assumes the following namespace aliases:

```clojure
(require '[preflex.resilient       :as r])
(require '[preflex.instrument      :as i])
(require '[preflex.instrument.jdbc :as j])
(require '[preflex.metrics         :as m])
(require '[preflex.type            :as t])
```


## Guaranteeing timeout with bounded thread pools

Preflex exposes API to create bounded thread pools and to invoke tasks on them.

```clojure
;; create a thread pool of maximum 10 threads and queue-size of 20
(def tp (r/make-bounded-thread-pool 10 20))

;; execute a task (task is a no-argument fn) via the thread pool, returning task result
(r/via-thread-pool tp {:task-timeout [1000 :millis]} #(+ 40 50))

;; only submit a task to the thread pool returning a future
(deref (r/future-call-via tp {:on-task-submit println} #(+ 40 50)))
```


## Restricting application overload with semaphores

A counting semaphore is useful to restrict the total number of concurrent tasks in a given context.

```clojure
;; restrict total permits to 10
(def sem (r/make-counting-semaphore 10))

;; only 10 such calls can happen simultaneously at any given time
(r/via-semaphore sem #(+ 40 50))
```

Sometimes, a binary semaphore may be a clever way to avoid a mutex typically for idempotent side effects.

```clojure
;; allows only one permit, effectively behaving like a lock
(def bi-sem (r/make-binary-semaphore))

;; use just like counting-semaphore (see example above)
(r/via-semaphore bi-sem #(+ 40 50))

;; specify a rejection handler to avoid exception when permit/lock not acquired
(r/via-semaphore bi-sem {:on-semaphore-reject (constantly nil)} #(+ 40 50))
```


## Cutting off execution with circuit breakers

A circuit breaker detects repeated faults and cuts off execution in that context.

```clojure
;; detect failure when there are total 20 errors in last 10 seconds
(def fd (r/make-rolling-fault-detector 20 [10 :seconds]))

;; allow calls only once every 5 seconds
(def rr (r/make-half-open-retry-resolver [5 :seconds]))

;; create the circuit breaker based on the fault detector and the retry resolver
(def cb (r/make-circuit-breaker fd rr))

;; execute potentially-faulty task via circuit breaker
(r/via-circuit-breaker cb #(if (even? (System/currentTimeMillis)) (+ 40 50) (throw (Exception. "test"))))
```


## Tracking success/failure of an operation

You can track the success/failure of an operation using an arity-1 fn that interprets argument value `true` as success
and `false` as failure. See the example below:

```clojure
(defn track-status
  [status?]
  (if status?
    (println "Success")
    (println "Failure")))

(r/via-success-failure-tracker track-status #(+ 40 50))
```

Preflex also has built-in transient metrics collection utility for quick reporting. See another example below:

```clojure
;; setup the metrics recorder (status of last 10 seconds)
(def status-metrics (m/make-rolling-boolean-counter :success :failure 10))
(defn status-tracker
  [status?]
  (t/record! status-metrics status?))

;; execution operations
(r/via-success-failure-tracker status-tracker #(+ 40 50))  ; success
(r/via-success-failure-tracker status-tracker #(* 80 90))  ; success
(r/via-success-failure-tracker status-tracker #(throw (Exception. "test")))  ; failure

;; retrieve the metrics
(deref status-metrics)
```


## Tracking latency of an operation

Knowing when a certain kind of operation is slowing down is quite helpful. Latency of an operation can be tracked
using an arity-2 fn as shown below:

```clojure
(defn track-latency
  [status? latency]
  (if status?
    (println "Success in" latency "ms")
    (println "Failure in" latency "ms")))

(r/via-latency-tracker track-latency #(+ 40 50))
```

Preflex offers rich latency tracking utility for better reporting. See the example below:

```clojure
;; setup the latency metrics recorder (latency of last 10 seconds)
(def latency-metrics (m/make-rolling-percentile-collector :latency [50 90 95 99 99.5] 10))
(defn latency-tracker
  [status? latency]
  (t/record! latency-metrics latency))

;; execute operations
(r/via-latency-tracker latency-tracker #(+ 40 50))
(r/via-latency-tracker latency-tracker #(+ 80 90))

;; retrieve the metrics
(deref latency-metrics)
```


## Falling back to alternate step on operation error

Often when an operation fails, we may want to try doing something else instead of propagating the error to the caller.
Preflex offers a way to fall back on alternate operations.

```clojure
; if the database call errors out, read from lossy summary cache
(r/via-fallback [#(read-from-summary-cache id)] #(read-from-database id))
```


## Instrumenting a thread-pool (any `java.util.concurrent.ExecutorService` instance)

When tasks in a thread-pool take longer than usual, we want to know how is the time spent for the tasks. In the example
below we instrument a thread-pool to capture the timestamp of every stage a task goes through.

```clojure
;; create a thread pool of maximum 10 threads and queue-size of 20
(def tp (r/make-bounded-thread-pool 10 20))

;; define an invoker to execute the task (submitted to the thread pool) as a no-arg fn
(defn invoker [g context] (println @context) (g))

;; instrument the thread-pool (see the docstring for arguments)
(def instrumented-thread-pool (i/instrument-thread-pool tp
                                (assoc i/shared-context-thread-pool-task-wrappers-millis
                                  :callable-decorator (i/make-shared-context-callable-decorator invoker)
                                  :runnable-decorator (i/make-shared-context-runnable-decorator invoker))))
```

When we submit a task to the instrumented thread pool, the captured event timestamps (so far) are printed before
executing the task.

```clojure
@(.submit ^java.util.concurrent.ExecutorService instrumented-thread-pool
   ^java.util.concurrent.Callable #(+ 10 20))
```


## Instrumenting a JDBC connection pool (any `javax.sql.DataSource` instance)

JDBC operations involve creating a connection, preparing a statement and executing the SQL. We can instrument a
connection pool to find out the time spent at each stage.

```clojure
(defn print-latency [event f] (let [start (System/currentTimeMillis)]
                                (try (f)
                                  (finally (printf "Event: %s, Latency: %d millis"
                                             event (- (System/currentTimeMillis) start))))))
;; assuming 'dbcp' is a connection pool
(def instrumented-dbcp (j/instrument-datasource dbcp
                         {:conn-creation-wrapper print-latency
                          :stmt-creation-wrapper print-latency
                          :sql-execution-wrapper print-latency}))
```

Now, whenever we execute an operation on `instrumented-dbcp` the time spent on each phase is printed separately.
