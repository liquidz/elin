(ns elin.component.handler
  (:require
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.handler.complete]
   [elin.handler.connect]
   [elin.handler.evaluate]
   [elin.handler.internal]
   [elin.handler.lookup]
   [elin.handler.navigate]
   [elin.message :as e.message]
   [elin.protocol.config :as e.p.config]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.component :as e.s.component]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.server :as e.s.server]
   [elin.util.server :as e.u.server]
   [malli.core :as m]
   [taoensso.timbre :as timbre]))

(m/=> resolve-handler [:=> [:cat e.s.component/?LazyHost qualified-symbol?]
                       [:or :nil [:cat qualified-keyword? fn?]]])
(defn- resolve-handler [lazy-host sym]
  (when-let [f (try
                 @(requiring-resolve sym)
                 (catch Exception _
                   (e.message/warning lazy-host "Failed to resolve handler:" sym)
                   nil))]
    [(keyword sym) f]))

(m/=> build-handler-map [:=> [:cat e.s.component/?LazyHost [:sequential qualified-symbol?]]
                         e.s.handler/?HandlerMap])
(defn- build-handler-map
  [lazy-host handler-symbols]
  (reduce (fn [accm sym]
            (if-let [[k f] (resolve-handler lazy-host sym)]
              (assoc accm k f)
              accm))
          {} handler-symbols))

(m/=> construct-handler-parameter [:=> [:cat map?] e.s.handler/?Elin])
(defn- construct-handler-parameter
  [{:as context :keys [message config-map]}]
  (let [{:component/keys [interceptor nrepl]} context
        {:as message' :keys [method]} (merge message
                                             (e.p.h.rpc/parse-message message))
        handler-config (or (get config-map (symbol method))
                           {})
        message-config (some-> (get-in message' [:options :config])
                               (edn/read-string))
        this-config (e.config/merge-configs handler-config
                                            message-config)
        interceptor' (when this-config
                       (e.p.config/configure interceptor this-config))
        context' (cond-> context
                   this-config
                   (assoc :component/interceptor interceptor'
                          :component/nrepl (assoc nrepl :interceptor interceptor')))]
    (assoc context' :message message')))

(defn- handler* [handler-map context]
  (:response
   (e.p.interceptor/execute
    (:component/interceptor context) e.c.interceptor/handler context
    (fn [{:as context :component/keys [host]}]
      (let [elin (construct-handler-parameter context)
            handler-key (get-in elin [:message :method])
            resp (if-let [handler-fn (get handler-map handler-key)]
                   (handler-fn elin)
                   (let [msg (format "Unknown handler: %s" handler-key)]
                     (e.message/error host msg)
                     msg))
            resp' (e.u.server/format resp)
            resp' (if-let [callback (get-in elin [:message :options :callback])]
                    (try
                      (e.p.rpc/notify-function host "elin#callback#call" [callback resp'])
                      ;; FIXME
                      (catch Exception ex
                        (e.message/error host "Failed to callback" (ex-message ex))))
                    resp')]
        (assoc context :response resp'))))))

(m/=> handler [:=> [:cat e.s.handler/?Components map? e.s.handler/?HandlerMap e.s.server/?Message]
               any?])
(defn- handler
  [components
   config-map
   handler-map
   message]
  (let [method (:method (e.p.h.rpc/parse-message message))
        context (assoc components :message message :config-map config-map)]
    (if-let [log-level (get-in config-map [(symbol method) :log :min-level])]
      (timbre/with-level log-level (handler* handler-map context))
      (handler* handler-map context))))

(defrecord Handler
  [;; COMPONENTS
   clj-kondo
   interceptor
   lazy-host
   nrepl
   plugin
   session-storage
   ;; CONFIGS
   includes
   excludes
   config-map
   initialize
   ;; PARAMS
   handler-map]
  component/Lifecycle
  (start [this]
    (let [components {:component/nrepl nrepl
                      :component/interceptor interceptor
                      :component/host lazy-host
                      :component/handler this
                      :component/session-storage session-storage
                      :component/clj-kondo clj-kondo}
          exclude-set (set excludes)
          handlers (concat (or includes [])
                           (or (get-in plugin [:loaded-plugin :handlers]) []))
          handlers (remove #(contains? exclude-set %) handlers)
          handler-map (build-handler-map lazy-host handlers)
          handler (partial handler components config-map handler-map)]
      (timbre/info "Handler component: Started")
      (assoc this
             :handler-map handler-map
             :handler handler)))

  (stop [this]
    (timbre/info "Handler component: Stopped")
    (dissoc this :handler :handler-map)))

(defn new-handler
  [config]
  (map->Handler (or (:handler config) {})))
