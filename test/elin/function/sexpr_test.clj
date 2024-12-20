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
