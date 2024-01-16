(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.handler.core :as e.h.core]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat e.u.schema/?ClientMessage] any?])
(defn- handler
  [msg]
  (let [{:as arg :keys [async?]} (merge msg
                                        (e.p.rpc/parse-message msg))]
    (if-not async?
      (e.h.core/handler* arg)
      (future
        (let [resp (e.h.core/handler* arg)
              {:keys [callback]} arg]
          (try
            (e.p.rpc/call-function msg "elin#callback#call" [callback resp])
            (catch Exception ex
              (e.log/error msg "Failed to callback" (ex-message ex)))))))))

(defrecord Handler
  []
  component/Lifecycle
  (start [this]
    (assoc this :handler handler))
  (stop [this]
    (dissoc this :handler)))
