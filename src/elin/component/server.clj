(ns elin.component.server
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.component.server.impl.buffer]
   [elin.component.server.impl.event]
   [elin.component.server.impl.file]
   [elin.component.server.impl.function]
   [elin.component.server.impl.io]
   [elin.component.server.impl.mark]
   [elin.component.server.impl.popup]
   [elin.component.server.impl.quickfix]
   [elin.component.server.impl.register]
   [elin.component.server.impl.select]
   [elin.component.server.impl.sexpr]
   [elin.component.server.impl.sign]
   [elin.component.server.impl.variable]
   [elin.component.server.impl.virtual-text]
   [elin.error :as e]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.lazy-host :as e.p.lazy-host]
   [taoensso.timbre :as timbre])
  (:import
   (java.net ServerSocket)))

(defn on-accept
  [handler lazy-host {:keys [message host]}]
  (e.p.lazy-host/set-host! lazy-host host)

  (if (e.p.h.rpc/response? message)
    ;; Receive response
    (let [{:keys [response-manager]} message
          {:keys [id error result]} (e.p.h.rpc/parse-message message)]
      (when-let [ch (get @response-manager id)]
        (swap! response-manager dissoc id)
        (async/put! ch {:result result :error error})))

    ;; Receive request/notify
    (future
      (let [[res err] (try
                        (let [res (handler message)]
                          (if (e/error? res)
                            [nil (ex-message res)]
                            [res]))
                        (catch Exception ex
                          [nil (ex-message ex)]))]
        (when (e.p.h.rpc/request? message)
          (e.p.h.rpc/response! lazy-host
                               (:id (e.p.h.rpc/parse-message message))
                               err res)
          (e.p.h.rpc/flush! lazy-host))))))

(defrecord Server
  [;; COMPONENTS
   handler
   lazy-host
   ;; CONFIGS
   entrypoints
   host
   port
   ;; PARAMS
   server
   server-socket
   stop-signal]
  component/Lifecycle
  (start [this]
    (when-not server
      (timbre/info "Server component: Starting" host port)
      (let [server-socket (ServerSocket. port)
            stop-signal (async/chan)
            handler' (:handler handler)
            server-arg {:host host
                        :server-socket server-socket
                        :on-accept (partial on-accept handler' lazy-host)
                        :stop-signal stop-signal}
            entrypoint-sym (get entrypoints host)
            _ (when-not entrypoint-sym
                (throw (e/unsupported {:message (format "Unknown host: %s" host)})))
            server (future
                     ((requiring-resolve entrypoint-sym) server-arg))]
        (timbre/info "Server component: Started" host port)
        (assoc this
               :stop-signal stop-signal
               :server server
               :server-socket server-socket))))

  (stop [this]
    (when server
      (timbre/info "Server component: Stopping" host port stop-signal)
      (.close server-socket)
      (async/put! stop-signal true)
      @server
      (async/close! stop-signal)
      (timbre/info "Server component: Stopped")
      (assoc this :server-socket nil :server nil))))

(defn new-server
  [config]
  (map->Server (or (:server config) {})))
