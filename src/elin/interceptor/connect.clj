(ns elin.interceptor.connect
  (:require
   [elin.protocol.nrepl :as e.p.nrepl]))

(def connect-interceptor
  {:name ::connect
   :enter (fn [{:as ctx :keys [client-manager host port]}]
            (let [client (e.p.nrepl/add-client! client-manager host port)]
              (e.p.nrepl/switch-client! client-manager client)
              (assoc ctx :client client)))})
