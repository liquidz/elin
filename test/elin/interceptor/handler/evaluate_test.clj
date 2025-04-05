(ns elin.interceptor.handler.evaluate-test
  (:require
   [clojure.test :as t]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.interceptor.handler.evaluate :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest hook-test
  (let [test-elin (-> (h/test-elin)
                      (assoc-in [:component/interceptor :config-map (symbol #'sut/hook)]
                                {:before '(before-code)
                                 :after '(after-code)}))
        evaluated (atom nil)
        {:keys [enter leave]} sut/hook]
    (with-redefs [e.f.evaluate/evaluate-code (fn [_ code _] (reset! evaluated code))]
      (t/testing "enter"
        (t/is (= test-elin
                 (enter test-elin)))
        (t/is (= "(before-code)" @evaluated)))

      (t/testing "leave"
        (t/is (= test-elin
                 (leave test-elin)))
        (t/is (= "(after-code)" @evaluated))))))
