(ns elin.util.process-test
  (:require
   [clojure.test :as t]
   [elin.util.process :as sut]))

(t/deftest start-test
  (t/testing "random process-id"
    (let [id (sut/start ["echo" "'foo'"])]
      (t/is (some? id))
      (t/is (true? (sut/alive? id)))
      (Thread/sleep 100)
      (t/is (false? (sut/alive? id)))))

  (t/testing "specified process-id"
    (let [id (random-uuid)]
      (t/is (= id (sut/start id ["echo" "'foo'"])))
      (t/is (true? (sut/alive? id)))
      (Thread/sleep 100)
      (t/is (false? (sut/alive? id))))))

(t/deftest kill-test
  (let [id (sut/start ["cat"])]
    (Thread/sleep 100)
    (t/is (true? (sut/alive? id)))
    (t/is (some? (sut/kill id)))

    (Thread/sleep 100)
    (t/is (false? (sut/alive? id)))
    (t/is (nil? (sut/kill id)))))
