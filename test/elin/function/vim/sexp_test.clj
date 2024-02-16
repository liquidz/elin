(ns elin.function.vim.sexp-test
  (:require
   [clojure.test :as t]
   [elin.function.vim.sexp :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(defn- get-namespace-host
  [ns-form]
  (let [handler #(if (h/call-function? % "elin#internal#sexp#clojure#get_ns_form")
                   ns-form
                   "")]
    (h/test-host {:handler handler})))

(t/deftest get-namespace-test
  (t/testing "no metadata"
    (t/is (= "foo.bar"
             (sut/get-namespace!! (get-namespace-host "(ns foo.bar)")))))

  (t/testing "with metadata"
    (t/is (= "foo.bar"
             (sut/get-namespace!! (get-namespace-host "(ns ^:meta foo.bar)"))))
    (t/is (= "foo.bar"
             (sut/get-namespace!! (get-namespace-host "(ns ^{:meta true} foo.bar)")))))

  (t/testing "in-ns"
    (t/is (= "foo.bar"
             (sut/get-namespace!! (get-namespace-host "(in-ns 'foo.bar)"))))))
