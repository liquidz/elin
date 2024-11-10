(ns elin.util.process-test
  (:require
   [clojure.test :as t]
   [elin.util.process :as sut]))

(t/deftest start-and-kill-test
  (t/testing "random process-id"
    (let [id (sut/start ["cat"])]
      (t/is (some? id))
      (Thread/sleep 100)
      (t/is (true? (sut/alive? id)))
      (t/is (some? (sut/kill id)))

      (Thread/sleep 100)
      (t/is (false? (sut/alive? id)))
      (t/is (nil? (sut/kill id)))))

  (t/testing "specified process-id"
    (let [id (random-uuid)]
      (t/is (= id (sut/start id ["cat"])))
      (Thread/sleep 100)
      (t/is (true? (sut/alive? id)))
      (t/is (some? (sut/kill id)))

      (Thread/sleep 100)
      (t/is (false? (sut/alive? id)))
      (t/is (nil? (sut/kill id))))))
