# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## TODO

- Generic kill switch
  - Protocl `ITerminable` with `terminate` and `terminated?` fns
  - Have the stateful abstractions (thread pool, semaphore etc.) implement `ITerminable`
- Instrumentation
  - JDBC (including top slow queries)
- Resilience primitives
  - Move to namespace `preflex.resilience`
  - Retry
  - Throttle
- Hystrix emulation
  - Command


## [WIP] 0.2.0 / 2017-July-??
### Fixed
- Fix race condition in thread-pool instrumentation - https://github.com/kumarshantanu/preflex/issues/1
- Fix arity-mismatch issue in `deref` use-case of `preflex.core/future-call-via`
- [TODO] Fast thread-pool instrumentation support with limited stages


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
