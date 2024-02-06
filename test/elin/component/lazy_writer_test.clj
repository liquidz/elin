(ns elin.component.lazy-writer-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest new-lazy-writer-test
  (let [{:as sys :keys [lazy-writer]} (-> (e.system/new-system)
                                          (select-keys [:lazy-writer])
                                          (component/start-system))
        wrote (atom [])
        writer (h/test-writer {:handler #(do (swap! wrote conj %)
                                             "OK")})]
    (try
      (e.p.rpc/notify! lazy-writer ["before"])
      (e.p.rpc/set-writer! lazy-writer writer)
      (e.p.rpc/notify! lazy-writer ["after"])

      (t/is (= [[2 "after"]] @wrote))

      (async/<!! (async/timeout 200))

      (t/is (= 2 (count @wrote)))
      (t/is (= #{[2 "before"] [2 "after"]}
               (set @wrote)))

      (finally
        (component/stop-system sys)))))
