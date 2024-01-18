(ns elin.component.server
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.component.server.nvim :as e.c.s.nvim]
   [elin.component.server.vim :as e.c.s.vim]
   [elin.constant.host :as e.c.host]
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
  [host port cwd server-socket server]
  component/Lifecycle
  (start [this]
    (when-not server
      (let [server-socket (ServerSocket. port)
            handler (:handler (:handler this))
            server-arg {:host host
                        :cwd cwd
                        :server-socket server-socket
                        :on-accept (partial on-accept handler)}
            server (future
                     (if (= e.c.host/nvim host)
                       (e.c.s.nvim/start-server server-arg)
                       (e.c.s.vim/start-server server-arg)))]
        (assoc this
               :server server
               :server-socket server-socket))))

  (stop [this]
    (when server
      (.close server-socket)
      @server
      (assoc this :server-socket nil :server nil))))
