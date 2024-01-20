(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.handler.core :as e.h.core]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat e.s.server/?Message] any?])
(defn- handler
  [components msg]
  (let [msg' (merge msg
                    (e.p.rpc/parse-message msg))
        params (assoc components
                      :message msg')
        resp (e.h.core/handler* params)]
    (if-let [callback (:callback msg')]
      (try
        (e.p.rpc/call-function msg "elin#callback#call" [callback resp])
        (catch Exception ex
          (e.log/error msg "Failed to callback" (ex-message ex))))
      resp)))

(defrecord Handler
  [nrepl]
  component/Lifecycle
  (start [this]
    (let [components {:nrepl nrepl}]
      (assoc this :handler (partial handler components))))
  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
