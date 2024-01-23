(ns elin.nrepl.client-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.nrepl.client :as sut]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(t/deftest connect-test
  (let [client (sut/connect "localhost" h/*nrepl-server-port*)]
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
                       (e.n.message/merge-messages))]
          (t/is (= #{:babashka :babashka.nrepl}
                   (set (keys (:versions resp))))))

        (t/testing "not supported op"
          (t/is (thrown-with-msg? Exception #"Not supported operation"
                  (e.p.nrepl/request client {:op "unknown"})))))

      (t/testing "notify and std out"
        (let [resp (e.p.nrepl/notify client {:op "eval" :code "(println \"hello\")"})]
          (t/is (nil? resp))
          (async/<!! (async/timeout 100))
          (t/is (= {:type "out" :text "hello\n"}
                   (async/<!! (get-in client [:connection :output-channel])))))

        (t/testing "not supported op"
          (t/is (thrown-with-msg? Exception #"Not supported operation"
                  (e.p.nrepl/notify client {:op "unknown"}))))))

    (t/testing "IClient"
      (t/testing "supported-op?"
        (t/is (true? (e.p.nrepl/supported-op? client "eval")))
        (t/is (false? (e.p.nrepl/supported-op? client "unknown")))))

    ;; (t/testing "INreplOp"
    ;;   (t/testing "eval-op"
    ;;     (let [resp (-> (e.p.nrepl/eval-op client "(+ 1 2 3)" {})
    ;;                    (async/<!!)
    ;;                    (e.n.message/merge-messages))]
    ;;       (t/is (= {:session (:session client)
    ;;                 :status ["done"]
    ;;                 :value "6"}
    ;;                (select-keys resp [:session :status :value])))))
    ;;
    ;;   #_(t/testing "interrupt-op")
    ;;   #_(t/testing "load-file-op")
    ;;
    ;;   (t/testing "ls-sessions"
    ;;     (t/is (= [(:session client)]
    ;;              (async/<!! (e.p.nrepl/ls-sessions client)))))
    ;;   (t/testing "close-op"))


    (t/testing "disconnect"
      (t/is (true? (e.p.nrepl/disconnect client)))
      (t/is (true? (e.p.nrepl/disconnected? client))))))
