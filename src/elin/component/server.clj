(ns elin.component.server
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.server.nvim :as e.c.s.nvim]
   [elin.component.server.vim :as e.c.s.vim]
   [elin.constant.host :as e.c.host])
  (:import
   java.net.ServerSocket))

(defrecord Server
  [host port server-socket server]
  component/Lifecycle
  (start [this]
    (when-not server
      (let [server-sock (ServerSocket. port)
            server-arg {:host host
                        :server-socket server-sock
                        :handler (:handler (:handler this))}
            server (future
                     (if (= e.c.host/nvim host)
                       (e.c.s.nvim/start-server server-arg)
                       (e.c.s.vim/start-server server-arg)))]
        (assoc this
               :server server
               :server-socket server-sock))))

  (stop [this]
    (when server
      (.close server-socket)
      @server
      (assoc this :server-socket nil :server nil))))
