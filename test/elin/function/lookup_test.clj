(ns elin.function.lookup-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.lookup :as sut]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest lookup-test
  (t/testing "positive"
    (t/testing "cider info"
      (t/testing "regular"
        (let [info-resp (h/dummy-lookup-response)]
          (with-redefs [e.f.n.cider/info!! (constantly info-resp)]
            (t/is (= info-resp
                     (sut/lookup (h/test-elin) "foo.bar" "baz"))))))

      ;; TODO
      (t/testing "protocol"))

    (t/testing "nrepl lookup"
      (t/testing "info does not respond namespace and var name"
        (let [info-resp {:status ["done"]}
              fallback-resp (h/dummy-lookup-response)]
          (with-redefs [e.f.n.cider/info!! (constantly info-resp)
                        e.f.clj-kondo/lookup (constantly fallback-resp)]
            (t/is (= fallback-resp
                     (sut/lookup (h/test-elin) "foo.bar" "baz")))))))))

(t/deftest clojuredocs-lookup
  (let [elin (h/test-elin)
        test-url "https://example.com"]
    (t/testing "Positive"
      (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                    e.f.sexpr/get-expr (constantly {:code "(bar)"})
                    e.f.sexpr/get-namespace (constantly "foo")
                    sut/lookup (constantly {:ns "foo" :name "bar"})
                    e.f.n.cider/clojuredocs-lookup!! (fn [_ ns-str sym-str edn-url]
                                                       (if (and (= "foo" ns-str)
                                                                (= "bar" sym-str)
                                                                (= test-url edn-url))
                                                         "SUCCESS"
                                                         (e/not-found)))]
        (t/is (= "SUCCESS"
                 (sut/clojuredocs-lookup elin test-url)))))

    (t/testing "Negative"
      (t/testing "Failed to get cursor position"
        (with-redefs [e.p.host/get-cursor-position! (h/async-constantly (e/fault {:message "test error"}))]
          (let [resp (sut/clojuredocs-lookup elin test-url)]
            (t/is (true? (e/fault? resp)))
            (t/is (= "test error" (ex-message resp))))))

      (t/testing "Failed to get expression"
        (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                      e.f.sexpr/get-expr (constantly (e/fault {:message "test error"}))]
          (let [resp (sut/clojuredocs-lookup elin test-url)]
            (t/is (true? (e/fault? resp)))
            (t/is (= "test error" (ex-message resp))))))

      (t/testing "Failed to lookup"
        (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                      e.f.sexpr/get-expr (constantly {:code "(bar)"})
                      e.f.sexpr/get-namespace (constantly "foo")
                      sut/lookup (constantly {:ns "foo" :name "bar"})
                      e.f.n.cider/clojuredocs-lookup!! (constantly (e/fault))]
          (let [resp (sut/clojuredocs-lookup elin test-url)]
            (t/is (true? (e/not-found? resp)))))))))
