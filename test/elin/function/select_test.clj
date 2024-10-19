(ns elin.function.select-test
  (:require
   [clojure.test :as t]
   [elin.function.callback :as e.f.callback]
   [elin.function.select :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest select-from-candidates-test
  (let [elin (h/test-elin)]
    (with-redefs [e.p.host/select-from-candidates (fn [_ _ _ [id]]
                                                    (e.f.callback/callback elin id "multiple"))]

      (t/is (= "multiple"
               (sut/select-from-candidates elin ["foo" "bar"])))

      (t/is (= "single"
               (sut/select-from-candidates elin ["single"])))

      (t/is (nil? (sut/select-from-candidates elin []))))))
