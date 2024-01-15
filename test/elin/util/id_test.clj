(ns elin.util.id-test
  (:require
   [clojure.test :as t]
   [elin.test-helper]
   [elin.util.id :as sut]))

(t/deftest next-id-test
  (t/is (apply not= (repeatedly 100 #(sut/next-id)))))
