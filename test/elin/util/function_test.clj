(ns elin.util.function-test
  (:require
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.function :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest memoize-by-test
  (let [called (atom [])
        test-fn* (fn [m]
                   (swap! called conj m)
                   (:value m))
        test-fn (sut/memoize-by (comp :value first) test-fn*)]
    (t/is (empty? @called))
    (t/is (= 1 (test-fn {:value 1})))
    (t/is (= [{:value 1}] @called))
    (t/is (= 1 (test-fn {:value 1})))
    (t/is (= [{:value 1}] @called))
    (t/is (= 9 (test-fn {:value 9})))
    (t/is (= [{:value 1} {:value 9}] @called))))
