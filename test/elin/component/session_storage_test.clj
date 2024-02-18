(ns elin.component.session-storage-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.session-storage :as sut]
   [elin.protocol.storage :as e.p.storage]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest new-session-storage-test
  (let [{:as sys :keys [session-storage]} (-> (e.system/new-system)
                                              (select-keys [:session-storage])
                                              (component/start-system))]
    (try
      (t/is (= "world" (e.p.storage/set session-storage :hello "world")))
      (t/is (= "world" (e.p.storage/get session-storage :hello)))
      (t/is (true? (e.p.storage/contains? session-storage :hello)))
      (e.p.storage/delete session-storage :hello)
      (t/is (nil? (e.p.storage/get session-storage :hello)))
      (t/is (false? (e.p.storage/contains? session-storage :hello)))

      (t/testing "expires"
        (t/is (= "limited" (e.p.storage/set session-storage :limited "limited" 100)))
        (t/is (= "limited" (e.p.storage/get session-storage :limited)))
        (with-redefs [sut/now (fn []
                                (+ (int (/ (System/currentTimeMillis) 1000))
                                   99))]
          (t/is (= "limited" (e.p.storage/get session-storage :limited)))
          (t/is (true? (e.p.storage/contains? session-storage :limited))))

        (with-redefs [sut/now (fn []
                                (+ (int (/ (System/currentTimeMillis) 1000))
                                   100))]
          (t/is (nil? (e.p.storage/get session-storage :limited)))
          (t/is (false? (e.p.storage/contains? session-storage :limited)))))

      (finally
        (component/stop-system sys)))))
