(ns elin.component.server
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.component.server.nvim :as e.c.s.nvim]
   [elin.component.server.vim :as e.c.s.vim]
   [elin.constant.host :as e.c.host]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc])
  (:import
   java.net.ServerSocket))

(defn on-accept
  [handler message]
  (if (e.p.rpc/response? message)
    ;; Receive response
    (let [{:keys [response-manager]} message
          {:keys [id error result]} (e.p.rpc/parse-message message)]
      (when-let [ch (get @response-manager id)]
        (swap! response-manager dissoc id)
        (async/go (async/>! ch {:result result :error error}))))

    ;; Receive request/notify
    (future
      (let [[res err] (try
                        [(handler message)]
                        (catch Exception ex
                          [nil (ex-message ex)]))]
        (when (e.p.rpc/request? message)
          (e.p.rpc/response! message err res)
          (.flush (:output-stream message)))))))

(defrecord Server
  [host port server-socket server stop-signal]
  component/Lifecycle
  (start [this]
    (when-not server
      (e.log/debug "Server component: Starting" host port)
      (let [server-socket (ServerSocket. port)
            stop-signal (async/chan)
            handler (:handler (:handler this))
            server-arg {:host host
                        :server-socket server-socket
                        :on-accept (partial on-accept handler)
                        :stop-signal stop-signal}
            server (future
                     (if (= e.c.host/nvim host)
                       (e.c.s.nvim/start-server server-arg)
                       (e.c.s.vim/start-server server-arg)))]
        (e.log/debug "Server component: Started" host port)
        (assoc this
               :stop-signal stop-signal
               :server server
               :server-socket server-socket))))

  (stop [this]
    (when server
      (e.log/debug "Server component: Stopping" host port stop-signal)
      (.close server-socket)
      (async/put! stop-signal true)
      @server
      (async/close! stop-signal)
      (e.log/debug "Server component: Stopped")
      (assoc this :server-socket nil :server nil))))

(defn new-server
  [config]
  (map->Server (:server config)))
