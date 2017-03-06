# preflex

A Clojure library for resilience, instrumentation and metrics.

**Currently in ALPHA. Expect breaking changes!**

_**Requires Clojure 1.7 or higher, Java 7 or higher.**_

Preflex provides the following facilities:

* Resilience
  * Thread-pool execution - provides guaranteed timeout
  * Semaphore execution - cuts off execution on overload, maintains tasks under a threshold
  * Circuit breaker - cuts off execution on repeated errors, auto-heals when error recovers
  * Fall-back execution - follows up a failed operation with fall-back operations
* Instrumentation
  * Success/failure tracking - tracks success/failure of an operation
  * Latency tracking - tracks latency of an operation
* Metrics
  * Ordinary counters
  * Rolling (sliding-window) counters
  * Rolling (sliding-window) store
  * Sharding with metrics collection/reporting

## Usage

Leiningen coordinates: `[preflex "0.1.0-SNAPSHOT"]` (not on Clojars yet)

See [documentation](doc/intro.md)

## License

Copyright © 2017 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
