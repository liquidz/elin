(ns elin.util.map-test
  (:require
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.map :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest select-keys-by-namespace-test
  (t/is (= {}
           (-> {:a 1 :b 2 :foo 3}
               (sut/select-keys-by-namespace :foo))))
  (t/is (= {:foo/a 1}
           (-> {:foo/a 1 :bar/b 2 :foo 3}
               (sut/select-keys-by-namespace :foo))))
  (t/is (= {:foo/a 1 :foo/b 2}
           (-> {:foo/a 1 :foo/b 2 :foo 3}
               (sut/select-keys-by-namespace :foo)))))
