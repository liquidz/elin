(ns elin.component.handler
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.handler.complete]
   [elin.handler.connect]
   [elin.handler.evaluate]
   [elin.handler.internal]
   [elin.handler.lookup]
   [elin.handler.navigate]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.server :as e.s.server]
   [elin.util.server :as e.u.server]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(def ^:private default-handlers
  '[elin.handler.complete/complete
    elin.handler.connect/connect
    elin.handler.evaluate/evaluate
    elin.handler.evaluate/evaluate-current-expr
    elin.handler.evaluate/evaluate-current-list
    elin.handler.evaluate/evaluate-current-top-list
    elin.handler.evaluate/load-current-file
    elin.handler.internal/initialize
    elin.handler.internal/intercept
    elin.handler.internal/error
    elin.handler.lookup/lookup
    elin.handler.navigate/jump-to-definition])

(m/=> resolve-handler [:=> [:cat e.s.server/?Writer qualified-symbol?]
                       [:or :nil [:cat qualified-keyword? fn?]]])
(defn- resolve-handler [lazy-writer sym]
  (when-let [f (try
                 @(requiring-resolve sym)
                 (catch Exception _
                   (e.log/warning lazy-writer "Failed to resolve handler:" sym)
                   nil))]
    [(keyword sym) f]))

(m/=> build-handler-map [:=> [:cat e.s.server/?Writer [:sequential qualified-symbol?]] e.s.handler/?HandlerMap])
(defn- build-handler-map
  [lazy-writer handler-symbols]
  (reduce (fn [accm sym]
            (if-let [[k f] (resolve-handler lazy-writer sym)]
              (assoc accm k f)
              accm))
          {} handler-symbols))

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
                        (let [msg (format "Unknown handler: %s" handler-key)]
                          (e.log/error writer msg)
                          msg))
                 resp' (e.u.server/format resp)
                 resp' (if-let [callback (get-in msg' [:options :callback])]
                         (try
                           (e.p.rpc/notify-function writer "elin#callback#call" [callback resp'])
                           ;; FIXME
                           (catch Exception ex
                             (e.log/error writer "Failed to callback" (ex-message ex))))
                         resp')]
             (assoc context :response resp'))))
        (:response))))

(defrecord Handler
  [interceptor     ; Interceptor component
   lazy-writer     ; LazyWriter component
   nrepl           ; Nrepl component
   plugin          ; Plugin component
   includes
   excludes
   handler-map]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor
                      :component/writer lazy-writer}
          exclude-set (set excludes)
          handlers (concat default-handlers
                           (or includes [])
                           (or (get-in plugin [:loaded-plugin :handlers]) []))
          handlers (remove #(contains? exclude-set %) handlers)
          handler-map (build-handler-map lazy-writer handlers)
          handler (partial handler components handler-map)]
      (assoc this
             :handler-map handler-map
             :handler handler)))

  (stop [this]
    (dissoc this :handler :handler-map)))

(defn new-handler
  [config]
  (map->Handler (or (:handler config) {})))
