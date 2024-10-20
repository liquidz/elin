(ns elin.component.nrepl.client-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.component.nrepl.client :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]
   [elin.util.nrepl :as e.u.nrepl]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(t/deftest connect-test
  (let [client (sut/connect {:host "localhost"
                             :port h/*nrepl-server-port*})
        raw-message-channel (get-in client [:connection :raw-message-channel])]
    (t/is (false? (e.p.nrepl/disconnected? client)))

    (t/testing "record values"
      (t/testing "session"
        (t/is (string? (:session client)))
        (t/is (seq (:session client))))

      (t/testing "supported-ops"
        (t/is (set? (:supported-ops client)))
        (t/is (seq (:supported-ops client))))

      (t/testing "version"
        (t/is (map? (:version client)))
        (t/is (seq (:version client)))))

    (t/testing "IConnection"
      (t/testing "request"
        (let [resp (-> (e.p.nrepl/request client {:op "describe"})
                       (async/<!!)
                       (e.u.nrepl/merge-messages))]
          (t/is (= #{:babashka :babashka.nrepl}
                   (set (keys (:versions resp))))))

        (t/testing "not supported op"
          (t/is (thrown-with-msg? Exception #"Not supported operation"
                  (e.p.nrepl/request client {:op "unknown"})))))

      (t/testing "notify and std out"
        (let [resp (e.p.nrepl/notify client {:op "eval" :code "(println \"hello\")"})]
          (t/is (nil? resp))
          (loop []
            (when-let [raw (async/<!! raw-message-channel)]
              (when (not=  "hello\n" (:out raw))
                (recur)))))

        (t/testing "not supported op"
          (t/is (thrown-with-msg? Exception #"Not supported operation"
                  (e.p.nrepl/notify client {:op "unknown"}))))))

    (t/testing "IClient"
      (t/testing "supported-op?"
        (t/is (true? (e.p.nrepl/supported-op? client "eval")))
        (t/is (false? (e.p.nrepl/supported-op? client "unknown")))))

    (t/testing "disconnect"
      (t/is (true? (e.p.nrepl/disconnect client)))
      (t/is (true? (e.p.nrepl/disconnected? client))))))
