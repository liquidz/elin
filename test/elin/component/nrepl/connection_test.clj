(ns elin.component.nrepl.connection-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.component.nrepl.connection :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]
   [elin.util.nrepl :as e.u.nrepl]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(t/deftest connect-test
  (let [conn (sut/connect "localhost" h/*nrepl-server-port*)]
    (t/is (false? (e.p.nrepl/disconnected? conn)))

    (t/testing "request"
      (let [resp (-> (e.p.nrepl/request conn {:op "describe"})
                     (async/<!!)
                     (e.u.nrepl/merge-messages))]
        (t/is (= #{:babashka :babashka.nrepl}
                 (set (keys (:versions resp)))))))

    (t/testing "notify and std out"
      (let [resp (e.p.nrepl/notify conn {:op "eval" :code "(println \"hello\")"})]
        (t/is (nil? resp))
        (loop []
          (when-let [raw (async/<!! (:raw-message-channel conn))]
            (when (not=  "hello\n" (:out raw))
              (recur))))))

    (t/testing "std err"
      (e.p.nrepl/request conn {:op "eval" :code "(binding [*out* *err*] (println \"world\"))"})
      (loop []
        (when-let [raw (async/<!! (:raw-message-channel conn))]
          (when (not=  "world\n" (:err raw))
            (recur)))))

    (t/testing "disconnect"
      (t/is (true? (e.p.nrepl/disconnect conn)))
      (t/is (true? (e.p.nrepl/disconnected? conn))))))
