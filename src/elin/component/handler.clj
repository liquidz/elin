(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.handler.core :as e.h.core]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat e.u.schema/?RequestMap] any?])
(defn- handler
  [req-map]
  (let [{:as arg :keys [async?]} (merge req-map
                                        (e.p.rpc/parse-request req-map))]
    (if-not async?
      (e.h.core/handler* arg)
      (future
        (let [resp (e.h.core/handler* arg)
              {:keys [callback]} arg]
          (try
            (e.p.rpc/call-function req-map "elin#callback#call" [callback resp])
            (catch Exception ex
              (e.log/log (str "FIXME callback error"
                              (ex-message ex))))))))))

(defrecord Handler
  []
  component/Lifecycle
  (start [this]
    (assoc this :handler handler))
  (stop [this]
    (dissoc this :handler)))
