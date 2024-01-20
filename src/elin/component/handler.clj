(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.kind :as e.c.kind]
   [elin.handler.core :as e.h.core]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [msgpack.clojure-extensions]))

(defn- generate-handler
  [{:as components :component/keys [interceptor]}]
  (fn [msg]
    (-> {:message msg}
        (as-> ctx
          (e.p.interceptor/execute
           interceptor e.c.kind/handler ctx
           (fn [{:as context :keys [message]}]
             (let [msg' (merge message
                               (e.p.rpc/parse-message message))
                   params (assoc components
                                 :message msg')
                   resp (e.h.core/handler* params)
                   resp' (if-let [callback (:callback msg')]
                           (try
                             (e.p.rpc/call-function msg "elin#callback#call" [callback resp])
                             (catch Exception ex
                               (e.log/error msg "Failed to callback" (ex-message ex))))
                           resp)]
               (assoc context :response resp')))))
        (:response))))

(defrecord Handler
  [nrepl interceptor]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor}
          handler (generate-handler components)]
      (assoc this
             :handler handler)))

  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
