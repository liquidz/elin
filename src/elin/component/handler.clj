(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.handler.connect]
   [elin.handler.evaluate]
   [elin.handler.internal]
   [elin.handler.navigate]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(def ^:private default-handlers
  '[elin.handler.connect/connect
    elin.handler.evaluate/evaluate
    elin.handler.evaluate/evaluate-current-expr
    elin.handler.evaluate/evaluate-current-list
    elin.handler.evaluate/evaluate-current-top-level
    elin.handler.internal/initialize
    elin.handler.internal/intercept
    elin.handler.navigate/jump-to-definition])

(m/=> build-handler-map [:=> [:cat e.s.handler/?Handlers] e.s.handler/?HandlerMap])
(defn- build-handler-map
  [handlers]
  (reduce (fn [accm sym]
            (if-let [f (try
                         (resolve sym)
                         (catch Exception _ nil))]
              (assoc accm (keyword sym) f)
              accm))
          {} handlers))

(m/=> handler [:=> [:cat e.s.handler/?Components e.s.handler/?HandlerMap e.s.server/?Message] any?])
(defn- handler
  [{:as components :component/keys [interceptor]}
   handler-map
   message]
  (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/handler %&)]
    (-> (assoc components :message message)
        (intercept
         (fn [{:as context :component/keys [writer] :keys [message]}]
           (let [msg' (merge message
                             (e.p.rpc/parse-message message))
                 elin (assoc context :message msg')
                 handler-key (:method msg')
                 resp (if-let [handler-fn (get handler-map handler-key)]
                        (handler-fn elin)
                        (e.log/error writer (format "Unknown handler: %s" handler-key)))
                 resp' (if-let [callback (:callback msg')]
                         (try
                           (e.p.rpc/notify-function writer "elin#callback#call" [callback resp])
                           ;; FIXME
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
          handler-map (build-handler-map default-handlers)

          handler (partial handler components handler-map)]
      (assoc this
             :handler handler)))

  (stop [this]
    (dissoc this :handler)))

(defn new-handler
  [_]
  (map->Handler {}))
