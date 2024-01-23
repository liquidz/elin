(ns elin.component.nrepl-test
  (:require
   [clojure.core.async :as async]
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(t/deftest new-nrepl-test
  (let [{:as sys :keys [nrepl]} (-> (e.system/new-system)
                                    (select-keys [:interceptor :nrepl])
                                    (component/start-system))]
    (try
      (t/is (nil? (e.p.nrepl/current-client nrepl)))

      (let [client (e.p.nrepl/add-client! nrepl "localhost" h/*nrepl-server-port*)]
        (t/is (true? (e.p.nrepl/switch-client! nrepl client)))
        (t/is (= client (e.p.nrepl/current-client nrepl)))
        (t/is (= {:status ["done"]
                  :session (:session client)
                  :value "6"}
                 (-> (e.p.nrepl/eval-op nrepl "(+ 1 2 3)" {})
                     (async/<!!)
                     (select-keys [:status :session :value]))))
        (t/is (= [(:session client)]
                 (async/<!! (e.p.nrepl/ls-sessions nrepl)))))

      (finally
        (component/stop-system sys)))))
