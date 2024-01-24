(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.kind :as e.c.kind]
   [elin.handler.core :as e.h.core]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> handler [:=> [:cat
                    [:map-of keyword? any?]
                    e.s.handler/?ArgMap]
               any?])
(defn- handler
  [{:as components :component/keys [nrepl interceptor]}
   arg-map]
  (let [intercept #(apply e.p.interceptor/execute interceptor e.c.kind/handler %&)]
    (-> arg-map
        (intercept
         (fn [{:as context :keys [message writer]}]
           (e.p.nrepl/set-writer! nrepl writer)

           (let [msg' (merge message
                             (e.p.rpc/parse-message message))
                 params (assoc components
                               :message msg'
                               :writer writer)
                 resp (e.h.core/handler* params)
                 resp' (if-let [callback (:callback msg')]
                         (try
                           (e.p.rpc/call-function writer "elin#callback#call" [callback resp])
                           (catch Exception ex
                             (e.log/error writer "Failed to callback" (ex-message ex))))
                         resp)]
             (assoc context :response resp'))))
        (:response))))

(defrecord Handler
  [nrepl interceptor]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor}
          handler (partial handler components)]
      (assoc this
             :handler handler)))

  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
