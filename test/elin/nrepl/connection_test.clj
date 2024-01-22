(ns elin.nrepl.connection-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.nrepl.connection]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-connection-fixture)

(t/deftest connect-test
  (let [conn h/*nrepl-connection*]
    (t/is (false? (e.p.nrepl/disconnected? conn)))

    (t/testing "request"
      (let [resp (-> (e.p.nrepl/request conn {:op "describe"})
                     (async/<!!)
                     (e.n.message/merge-messages))]
        (t/is (= #{:babashka :babashka.nrepl}
                 (set (keys (:versions resp)))))))

    (t/testing "notify and std out"
      (let [resp (e.p.nrepl/notify conn {:op "eval" :code "(println \"hello\")"})]
        (t/is (nil? resp))
        (async/<!! (async/timeout 100))
        (t/is (= {:type "out" :text "hello\n"}
                 (async/<!! (:output-channel conn))))))

    ;; TODO
    (t/testing "pprint-out")
    (t/testing "std err")

    (t/testing "disconnect"
      (t/is (true? (e.p.nrepl/disconnect conn)))
      (t/is (true? (e.p.nrepl/disconnected? conn))))))
