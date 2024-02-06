(ns elin.component.handler-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(defn test-handler
  [{:component/keys [writer]}]
  (e.p.rpc/echo-text writer "Hello world")
  "OK")

(t/deftest new-handler-test
  (let [test-handler-sym (symbol #'test-handler)
        config {:handler {:includes [test-handler-sym]}}
        {:as sys :keys [handler lazy-writer]} (-> (e.system/new-system config)
                                                  (dissoc :server)
                                                  (component/start-system))
        writer (h/test-writer {:handler (constantly true)})]
    (try
      (e.p.rpc/set-writer! lazy-writer writer)

      (let [handler-fn (:handler handler)
            res (handler-fn (h/test-message [0 1 (str test-handler-sym)]))]
        (t/is (= "OK" res))
        (t/is (= ["Hello world"] (h/get-outputs writer))))

      (finally
        (component/stop-system sys)))))
