(ns elin.function.callback-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [elin.function.callback :as sut]
   [elin.protocol.storage :as e.p.storage]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest register-test
  (let [{:as elin :component/keys [session-storage]} (h/test-elin)
        [id ch] (sut/register elin)]
    (t/is (= ch
             (e.p.storage/get session-storage id)))))

(t/deftest callback-test
  (let [elin (h/test-elin)
        [id ch] (sut/register elin)]
    (sut/callback elin id {:this {:is "result"}})
    (t/is (= {:this {:is "result"}}
             (async/<!! ch)))))
