(ns elin.handler.lookup-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.handler.lookup :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]
   [elin.util.file :as e.u.file]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(defn- mock-lookup
  [resp]
  (fn [_ _ _ config]
    (when-not config
      (throw (ex-info "lookup config is not passed" {})))
    resp))

(t/deftest show-source-test
  (let [elin (assoc-in (h/test-elin)
                       [:component/handler :config-map (symbol #'sut/show-source)]
                       {:lookup-config {:order [:nrepl]}})]
    (t/testing "Positive"
      (t/testing "Defined vars"
        (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                      e.f.sexpr/get-expr (constantly {:code "foo/bar"})
                      e.f.sexpr/get-namespace (constantly "foo")
                      e.f.lookup/lookup (mock-lookup {:file "foo.txt" :line 1 :column 1})
                      e.u.file/slurp (constantly "(foo bar)")]
          (t/is (= "(foo bar)"
                   (sut/show-source elin)))))

      (t/testing "Local bindings"
        (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                      e.f.sexpr/get-expr (constantly {:code "foo/bar"})
                      e.f.sexpr/get-namespace (constantly "foo")
                      e.f.lookup/lookup (mock-lookup {:file "foo.txt" :line 1 :column 7 :local? true})
                      e.u.file/slurp (constantly "(let [foo (bar\n            baz)]\n  foo)")]
          (t/is (= "foo (bar\n      baz)"
                   (sut/show-source elin))))))

    (t/testing "Negative"
      (with-redefs [e.p.host/get-cursor-position! (h/async-constantly {:lnum 1 :col 1})
                    e.f.sexpr/get-expr (constantly {:code "foo/bar"})
                    e.f.sexpr/get-namespace (constantly "foo")
                    e.f.lookup/lookup (mock-lookup {:file "foo.txt" :line 1 :column 1})
                    e.u.file/slurp (constantly (e/not-found))]
        (t/is (e/not-found? (sut/show-source elin)))))))
