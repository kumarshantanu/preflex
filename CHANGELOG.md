# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## TODO

- Instrumentation
  - JDBC (including top slow queries)
- Resilience primitives
  - Move to namespace `preflex.resilience`
  - Retry
  - Throttle
- Hystrix emulation
  - Command


## 0.2.0-alpha1 / 2017-April-17

### Added

- Instrumentation
  - Thread pool

### Changed

- Resilience primitives
  - Rename optional argument names (API cleanup)
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
- Metrics primitives in namespace `preflex.metrics`
- Instrumentation
  - Task definition
