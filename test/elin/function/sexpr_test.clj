(ns elin.function.sexpr-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.sexpr :as sut]
   [elin.test-helper :as h]
   [elin.test-helper.host]))

(t/use-fixtures :once h/malli-instrument-fixture)

(defn- get-namespace-elin
  [ns-form]
  (h/test-elin {:host {:get-namespace-form! ns-form}}))

(t/deftest get-namespace-test
  (t/testing "no metadata"
    (t/is (= "foo.bar"
             (sut/get-namespace (get-namespace-elin "(ns foo.bar)")))))

  (t/testing "with metadata"
    (t/is (= "foo.bar"
             (sut/get-namespace (get-namespace-elin "(ns ^:meta foo.bar)"))))
    (t/is (= "foo.bar"
             (sut/get-namespace (get-namespace-elin "(ns ^{:meta true} foo.bar)")))))

  (t/testing "in-ns"
    (t/is (= "foo.bar"
             (sut/get-namespace (get-namespace-elin "(in-ns 'foo.bar)")))))

  (t/testing "no namespace"
    (t/is (e/not-found?
           (sut/get-namespace (get-namespace-elin "(foo)"))))
    (t/is (e/not-found?
           (sut/get-namespace (get-namespace-elin ""))))))
