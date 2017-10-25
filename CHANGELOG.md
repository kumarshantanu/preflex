# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## TODO

- Generic kill switch
  - Protocl `ITerminable` with `terminate` and `terminated?` fns
  - Have the stateful abstractions (thread pool, semaphore etc.) implement `ITerminable`
- Resilience primitives
  - Retry
  - Throttle
- Instrumentation
  - http://micrometer.io/
  - Out of the box Class loader, Garbage collection, processor utilization, thread pools instrumentation


## [WIP] 0.4.0-alpha3 / 2017-October-??
### Added
- Add 'either' fault-handling abstraction in `preflex.either` ns (adaptation of the either-monad)
  - Helper fns `success`, `failure`
  - The `bind` (and `bind-deref`) dispatcher
  - Helpers `do-either`, `deref-either`, `either`
  - [TODO] `bind->` and `bind-as->`
  - [TODO] Remove `bind-deref`
- Add Hystrix-metrics emulation helpers
  - Command metrics
  - Thread pool metrics
  - Metrics collectors for use with resilience primitives
  - Metrics reporters for emitting metrics data
  - Tests for collecting and reporting metrics
  - [TODO] Tests for command metrics
  - [TODO] Tests for thread-pool metrics
  - [TODO] Tests for emitting metrics and visualization with Hystrix-dashboard

### Changed
- Resilience primitives
  - [BREAKING CHANGE] Move namespaces
    - `preflex.core`  to `preflex.resilient`
    - `preflex.error` to `preflex.resilient.error`
    - `preflex.impl`  to `preflex.resilient.impl`
  - [BREAKING CHANGE] Overhaul duration and time arguments in `preflex.resilient` namespace
    - In function `preflex.resilient/make-discrete-fault-detector`
      - Change argument `connected-until-duration` to duration instead of milliseconds
      - Rename option `:now-finder` to `:now-millis-finder` - time calculation in milliseconds
    - In function `preflex.resilient/make-rolling-fault-detector`
      - Change argument `connected-until-duration` from milliseconds to duration
      - Change option `:bucket-interval` from milliseconds to duration
    - In function `preflex.resilient/make-half-open-retry-resolver`
      - Change argument `half-open-duration` from milliseconds to duration
      - Rename option `:now-finder` to `:now-millis-finder` - time calculation in milliseconds
      - Change option `:open-duration` from milliseconds to duration
- [BREAKING CHANGE] Update `preflex.type.IDuration` abstraction
  - Add `duration?` protocol function
  - Rename protocol functions `duration-time` to `dur-time` and `duration-unit` to `dur-unit`
  - Add protocol functions `days`, `hours`, `minutes`, `seconds`, `millis`, `micros`, `nanos`
- JDBC Instrumentation
  - Applicable to `preflex.instrument.jdbc/instrument-connection` and `preflex.instrument.jdbc/instrument-datasource`
  - [BREAKING CHANGE] Connection event wrapper (option `:conn-creation-wrapper`) now handles create and close events
    - Useful to find number of busy connections
  - [BREAKING CHANGE] Statement event wrapper (option `:stmt-creation-wrapper`) now handles create and close events
    - Useful to find number of busy statements


## 0.3.0 / 2017-August-28
### Added
- JDBC instrumentation in `preflex.instrument.jdbc` namepace

### Changed
- Thread pool instrumentation
  - [BREAKING CHANGE] Replace event-handler with task-wrapper
  - Use shared-context initialized as `(atom {})` by default
  - [BREAKING CHANGE] Drop `preflex.instrument.concurrent.ConcurrentEventHandlerFactory` in favor of task wrappers


## 0.2.0 / 2017-July-05
### Fixed
- Fix race condition in thread-pool instrumentation - https://github.com/kumarshantanu/preflex/issues/1
- Fix arity-mismatch issue in `deref` use-case of `preflex.core/future-call-via`


## 0.2.0-beta1 / 2017-May-25
### Added
- Binary semaphore with `preflex.core/make-binary-semaphore`
- Optional kwarg `:fair?` in `preflex.core/make-circuit-breaker`

### Changed
- Protocol fn `preflex.type.IReinitializable/reinit!` is now potentially asynchronous
- Protocol fn `preflex.type.IMetricsRecorder/record!` is now potentially asynchronous

### Fixed
- Use binary semaphore instead of `clojure.core/locking` (mutex) in idempotent scenarios (circuit breaker impl)


## 0.2.0-alpha2 / 2017-April-20
### Fixed
- Fix issue where the fallback fns are invoked ahead of the primary fn in `preflex.core/via-fallback`
- Calculate `queue-duration` as `exec-begin-ts - submit-begin-ts` in thread-pool instrumentation


## 0.2.0-alpha1 / 2017-April-17
### Added
- Instrumentation
  - Thread pool

### Changed
- Resilience primitives
  - [BREAKING CHANGE] Rename optional argument names (API cleanup)
    - `preflex.core/make-bounded-thread-pool`
      - `:thread-pool-name` to `:name`
    - `preflex.core/make-counting-semaphore`
      - `:semaphore-name` to `:name`
      - `:semaphore-fair?` to `:fair?`
    - `preflex.core/make-circuit-breaker`
      - `:circuit-breaker-name` to `:name`


## 0.1.0-alpha1 / 2017-March-07
### Added
- Resilience primitives in namespace `preflex.core`
  - Bounded thread pool
  - Counting semahore
  - Circuit breaker
  - Success/failure tracker
  - Fallback
- Metrics primitives in namespace `preflex.metrics`
  - Ordinary counter
  - Rolling counter
  - Rolling collector
- Instrumentation
  - Task definition
