(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.handler :as e.handler]
   [elin.handler.connect]
   [elin.handler.core]
   [elin.handler.evaluate]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat e.s.handler/?Components e.s.server/?Message] any?])
(defn- handler
  [{:as components :component/keys [interceptor]}
   message]
  (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/handler %&)]
    (-> (assoc components :message message)
        (intercept
         (fn [{:as context :component/keys [writer] :keys [message]}]
           (let [msg' (merge message
                             (e.p.rpc/parse-message message))
                 params (assoc context :message msg')
                 resp (e.handler/handler* params)
                 resp' (if-let [callback (:callback msg')]
                         (try
                           (e.p.rpc/call-function writer "elin#callback#call" [callback resp])
                           (catch Exception ex
                             (e.log/error writer "Failed to callback" (ex-message ex))))
                         resp)]
             (assoc context :response resp'))))
        (:response))))

(defrecord Handler
  [nrepl interceptor lazy-writer]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor
                      :component/writer lazy-writer}
          handler (partial handler components)]
      (assoc this
             :handler handler)))

  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
