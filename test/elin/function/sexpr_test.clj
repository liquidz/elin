(ns elin.function.sexpr-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.sexpr :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]
   [elin.test-helper.host]))

(t/use-fixtures :once h/malli-instrument-fixture)

(defn- mock-get-namespace-sexpr!
  [ns-form]
  (fn [& _]
    (async/go
      {:code ns-form :lnum 0 :col 0})))

(t/deftest get-top-list-test
  (let [test-elin (h/test-elin)]
    (t/testing "Positive"
      (with-redefs [e.p.host/get-top-list-sexpr! (h/async-constantly {:code "foo" :lnum 1 :col 2})]
        (t/is (= {:code "foo" :lnum 1 :col 2}
                 (sut/get-top-list test-elin 0 0)))))

    (t/testing "Negative"
      (t/testing "Failed to fetch"
        (with-redefs [e.p.host/get-top-list-sexpr! (h/async-constantly (e/fault))]
          (t/is (e/fault? (sut/get-top-list test-elin 0 0)))))

      (t/testing "No code"
        (with-redefs [e.p.host/get-top-list-sexpr! (h/async-constantly {:code "" :lnum 0 :col 0})]
          (t/is (e/not-found? (sut/get-top-list test-elin 0 0))))))))

(t/deftest get-list-test
  (let [test-elin (h/test-elin)]
    (t/testing "Positive"
      (with-redefs [e.p.host/get-list-sexpr! (h/async-constantly {:code "foo" :lnum 1 :col 2})]
        (t/is (= {:code "foo" :lnum 1 :col 2}
                 (sut/get-list test-elin 0 0)))))

    (t/testing "Negative"
      (t/testing "Failed to fetch"
        (with-redefs [e.p.host/get-list-sexpr! (h/async-constantly (e/fault))]
          (t/is (e/fault? (sut/get-list test-elin 0 0)))))

      (t/testing "No code"
        (with-redefs [e.p.host/get-list-sexpr! (h/async-constantly {:code "" :lnum 0 :col 0})]
          (t/is (e/not-found? (sut/get-list test-elin 0 0))))))))

(t/deftest get-expr-test
  (let [test-elin (h/test-elin)]
    (t/testing "Positive"
      (with-redefs [e.p.host/get-single-sexpr! (h/async-constantly {:code "foo" :lnum 1 :col 2})]
        (t/is (= {:code "foo" :lnum 1 :col 2}
                 (sut/get-expr test-elin 0 0)))))

    (t/testing "Negative"
      (t/testing "Failed to fetch"
        (with-redefs [e.p.host/get-single-sexpr! (h/async-constantly (e/fault))]
          (t/is (e/fault? (sut/get-expr test-elin 0 0)))))

      (t/testing "No code"
        (with-redefs [e.p.host/get-single-sexpr! (h/async-constantly {:code "" :lnum 0 :col 0})]
          (t/is (e/not-found? (sut/get-expr test-elin 0 0))))))))

(t/deftest get-namespace-test
  (let [test-elin (h/test-elin)]
    (t/testing "no metadata"
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "(ns foo.bar)")]
        (t/is (= "foo.bar"
                 (sut/get-namespace test-elin)))))

    (t/testing "with metadata"
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "(ns ^:meta foo.bar)")]
        (t/is (= "foo.bar"
                 (sut/get-namespace test-elin))))
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "(ns ^{:meta true} foo.bar)")]
        (t/is (= "foo.bar"
                 (sut/get-namespace test-elin)))))

    (t/testing "in-ns"
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "(in-ns 'foo.bar)")]
        (t/is (= "foo.bar"
                 (sut/get-namespace test-elin)))))

    (t/testing "no namespace"
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "(foo)")]
        (t/is (e/not-found?
                (sut/get-namespace test-elin))))
      (with-redefs [e.p.host/get-namespace-sexpr! (mock-get-namespace-sexpr! "")]
        (t/is (e/not-found?
                (sut/get-namespace test-elin)))))))
