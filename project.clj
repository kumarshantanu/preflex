(defproject preflex "0.4.0-alpha2-SNAPSHOT"
  :description "Metrics, Instrumentation and Resilience for Clojure"
  :url "https://github.com/kumarshantanu/preflex"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :java-source-paths ["java-src"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :global-vars {*warn-on-reflection* true
                *assert* true
                *unchecked-math* :warn-on-boxed}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.7.0"]]
                        :global-vars {*unchecked-math* :warn-on-boxed}}
             :dev {:dependencies [[asphalt             "0.6.3"]
                                  [clj-dbcp            "0.9.0"]
                                  [com.h2database/h2   "1.3.176"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c19 {:dependencies [[org.clojure/clojure "1.9.0-beta1"]]}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :deploy-repositories [["releases" {:url "https://clojars.org" :creds :gpg}]])
