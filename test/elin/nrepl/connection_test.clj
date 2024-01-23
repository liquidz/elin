(ns elin.nrepl.connection-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.nrepl.connection :as sut]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(t/deftest connect-test
  (let [conn (sut/connect "localhost" h/*nrepl-server-port*)]
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

    (t/testing "std err"
      (e.p.nrepl/request conn {:op "eval" :code "(binding [*out* *err*] (println \"world\"))"})
      (t/is (= {:type "err" :text "world\n"}
               (async/<!! (:output-channel conn)))))

    (t/testing "disconnect"
      (t/is (true? (e.p.nrepl/disconnect conn)))
      (t/is (true? (e.p.nrepl/disconnected? conn))))))
