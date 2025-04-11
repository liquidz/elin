(ns elin.handler.test-test
  (:require
   [clojure.test :as t]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.function.storage.test :as e.f.s.test]
   [elin.handler.test :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest run-all-tests-test
  (let [{:as test-elin :component/keys [host session-storage]} (h/test-elin)]
    (with-redefs [e.f.sexpr/get-namespace (constantly "foo.core")
                  e.p.host/get-current-file-path! (h/async-constantly "/path/to/file.clj")
                  e.f.n.cider/test-var-query!! (constantly {:result "all-tests"})]
      (t/testing "Plain REPL"
        (try
          (t/is (nil? (sut/run-all-tests test-elin)))
          (t/is (= ["This feature is not supported in plain nREPL."]
                   (h/get-outputs host)))
          (t/is (nil? (e.f.s.test/get-last-test-query session-storage)))
          (finally
            (h/clear-outputs host))))

      (t/testing "cider-nrepl"
        (with-redefs [e.p.nrepl/supported-op? (constantly true)]
          (t/is (some? (sut/run-all-tests test-elin))))

        (t/is (empty? (h/get-outputs host)))
        (t/is (= {:project? true
                  :ns "foo.core"
                  :vars []
                  :current-file "/path/to/file.clj"
                  :base-line 0}
                 (e.f.s.test/get-last-test-query session-storage)))))))
