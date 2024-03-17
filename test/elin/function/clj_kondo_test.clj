(ns elin.function.clj-kondo-test
  (:require
   [clojure.test :as t]
   [elin.function.clj-kondo :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest namespaces-by-alias-test
  (let [c (h/test-clj-kondo)]
    (t/is (= '[elin.util.id]
             (sut/namespaces-by-alias c 'e.u.id)))
    (t/is (empty? (sut/namespaces-by-alias c 'non-existing-alias)))))

(t/deftest requiring-namespaces-test
  (let [c (h/test-clj-kondo)]
    (t/is (= '[malli.core]
             (sut/requiring-namespaces c "elin.util.id")))
    (t/is (empty? (sut/requiring-namespaces c "non-existing-ns")))))
