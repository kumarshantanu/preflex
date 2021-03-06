# preflex

A Clojure library for resilience, instrumentation and metrics.

_**Requires Clojure 1.7 or higher, Java 7 or higher.**_

Preflex provides the following facilities:

* Resilience
  * Thread-pool execution - provides guaranteed timeout
  * Semaphore execution - cuts off execution on overload, maintains tasks under a threshold
  * Circuit breaker - cuts off execution on repeated errors, auto-heals when error recovers
  * Fall-back execution - follows up a failed operation with fall-back operations
  * Either result - fault-handling across units of functionality
* Instrumentation
  * Success/failure tracking - tracks success/failure of an operation
  * Latency tracking - tracks latency of an operation
  * Thread-pool instrumentation - task wrapping for all stages (task submit, exec begin/end, future, shared state)
  * JDBC instrumentation - task wrapping for all stages (connection create, statement create, SQL execute)
* Metrics
  * Ordinary counters
  * Rolling (sliding-window) counters
  * Rolling (sliding-window) store
  * Sharding with metrics collection/reporting


## Usage

Clojars coordinates: `[preflex "0.4.0"]`

Github coordinates:

``` edn
{:deps {preflex {:git/url "https://github.com/kumarshantanu/preflex.git"
                 :sha "31d6e86b19710f1875c9a5bc26ce4886f07bc2f9"}}}
```

[![Cljdocs alpha](https://img.shields.io/badge/cljdoc-0.4.0-blue.svg)](https://cljdoc.xyz/d/preflex/preflex/0.4.0/)

See [documentation](doc/intro.md)


## License

Copyright © 2017-2018 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
