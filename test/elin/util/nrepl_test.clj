(ns elin.util.nrepl-test
  (:require
   [clojure.test :as t]
   [elin.util.nrepl :as sut]))

(t/deftest update-messages-test
  (let [dummy-message [{:status "done"}
                       {:key "hello"}]]
    (t/is (= [{:status "done"}
              {:key "hello world"}]
             (sut/update-messages :key #(str % " world") dummy-message)))
    (t/is (= [{:status "done"}
              {:key "hello"}
              {:unknown " world"}]
             (sut/update-messages :unknown #(str % " world") dummy-message)))))
