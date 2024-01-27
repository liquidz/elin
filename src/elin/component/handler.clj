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
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat
                    [:map-of keyword? any?]
                    e.s.handler/?ArgMap]
               any?])
(defn- handler
  [{:as components :component/keys [interceptor writer-store]}
   arg-map]
  (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/handler %&)]
    (-> arg-map
        (intercept
         (fn [{:as context :keys [message writer]}]
           (e.p.rpc/set-writer! writer-store writer)

           (let [msg' (merge message
                             (e.p.rpc/parse-message message))
                 params (assoc components
                               :message msg'
                               :writer writer)
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
  [nrepl interceptor writer-store]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor
                      :component/writer-store writer-store}
          handler (partial handler components)]
      (assoc this
             :handler handler)))

  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
