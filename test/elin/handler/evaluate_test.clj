(ns elin.handler.evaluate-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.handler.evaluate :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest expand-1-test
  (let [dummy-ns "foo.core"
        dummy-code "(dummy)"
        elin (-> (h/test-elin)
                 (assoc :message {:params [dummy-code]}))]
    (with-redefs [e.f.sexpr/get-namespace (constantly dummy-ns)
                  e.f.evaluate/expand-1 (fn [_ ns-str code]
                                          (if (and (= dummy-ns ns-str)
                                                   (= dummy-code code))
                                            {:value (pr-str '(expanded (code)))}
                                            (e/fault)))]
      (t/is (= "(expanded (code))\n"
               (sut/expand-1 elin))))))
