(ns elin.function.nrepl-test
  (:require
   [clojure.test :as t]
   [elin.function.nrepl :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(defn- test-handler [{:keys [op code]}]
  (when (= "eval" op)
    [{:value (str "code:" code)}]))

(defn- test-middleware [eval-fn]
  (fn [code option]
    (eval-fn (str code "!") option)))

(t/deftest eval!!-test
  (let [nrepl (h/test-nrepl {:client {:handler test-handler}})]
    (t/is (= {:value "code:hello"}
             (sut/eval!! nrepl "hello")))

    (t/testing "middleware"
      (t/is (= {:value "code:world!"}
               (sut/eval!! nrepl "world" {:middleware test-middleware}))))))
