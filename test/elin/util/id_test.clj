(ns elin.util.id-test
  (:require
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.id :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest next-id-test
  (t/is (apply not= (repeatedly 100 #(sut/next-id)))))
