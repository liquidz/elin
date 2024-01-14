(ns elin.interceptor.connect
  (:require
   [elin.nrepl.protocol :as e.n.protocol]))

(def connect-interceptor
  {:name ::connect
   :enter (fn [{:as ctx :keys [client-manager host port]}]
            (let [client (e.n.protocol/add-client! client-manager host port)]
              (e.n.protocol/switch-client! client-manager client)
              (assoc ctx :client client)))})
