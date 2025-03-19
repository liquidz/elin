(ns elin.handler.navigate-test
  (:require
   [clojure.test :as t]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.handler.navigate :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

;; (defn- mock-lookup
;;   [resp]
;;   (fn [_ _ _ config]
;;     (when-not config
;;       (throw (ex-info "lookup config is not passed" {})))
;;     resp))

;; (t/deftest jump-to-definition-test
;;   (with-redefs [e.p.host/get-cursor-position!]))

(t/deftest cycle-source-and-test-test
  (let [elin (h/test-elin)]
    (t/testing "src -> test"
      (with-redefs [e.p.host/get-current-file-path! (h/async-constantly "/src/foo/core.clj")
                    e.f.sexpr/get-namespace (constantly "foo.core")]
        (t/is (= {:path "/test/foo/core_test.clj"
                  :lnum -1
                  :col -1}
                 (sut/cycle-source-and-test elin)))))

    (t/testing "test -> src"
      (with-redefs [e.p.host/get-current-file-path! (h/async-constantly "/test/foo/core_test.clj")
                    e.f.sexpr/get-namespace (constantly "foo.core-test")]
        (t/is (= {:path "/src/foo/core.clj"
                  :lnum -1
                  :col -1}
                 (sut/cycle-source-and-test elin)))))))

;; (t/deftest cycle-function-and-test-test
;;   (let [elin (assoc-in (h/test-elin)
;;                        [:component/handler :config-map (symbol #'sut/show-source)]
;;                        {:lookup-config {:order [:nrepl]}})]
;;     (with-redefs [e.f.evaluate/get-var-name-from-current-top-list (constantly {:options {:ns "foo" :var-name "bar" :file "foo.txt"}})
;;                   e.u.file/guess-file-separator (constantly "foo")
;;                   e.f.lookup/lookup (mock-lookup {:file "foo.txt" :line 1 :column 1})])))
