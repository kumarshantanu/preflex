;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.either-test
  (:require
    [clojure.test :refer :all]
    [preflex.either :as either]))


(deftest test-service->web
  (is (thrown? IllegalArgumentException
        (either/bind :foo identity)))
  (is :foo (-> (either/do-either :foo)
             (either/bind identity)))
  (is (thrown? IllegalStateException
        (-> (either/do-either (throw (IllegalStateException. "test error")))
          (either/bind #(throw %) identity))))
  (is [:foo] (either/bind-> (either/do-either :foo)
               ((either/either vector))
               identity))
  (is (thrown? IllegalStateException
        (either/bind-> (either/do-either (throw (IllegalStateException. "test error")))
          (#(throw %) identity)))))
