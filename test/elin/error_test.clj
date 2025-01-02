(ns elin.error-test
  (:require
   [clojure.test :as t]
   [elin.error :as sut]))

(t/deftest error-or-test
  (t/is (= 1 (sut/error-or 1)))
  (t/is (= 1 (sut/error-or 1 (sut/fault) 2)))
  (t/is (= 2 (sut/error-or (sut/fault) 2)))
  (t/is (nil? (sut/error-or (sut/fault))))

  (let [called? (atom false)]
    (t/is (= 1 (sut/error-or 1
                             (do (reset! called? true)
                                 (sut/fault)))))
    (t/is (false? @called?))))
