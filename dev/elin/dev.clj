(ns elin.dev
  (:require
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.system :as e.system]
   [malli.dev :as m.dev]
   [taoensso.timbre :as timbre]))

(def server-config (atom {}))
(defonce sys (atom nil))

(defn initialize
  [{:keys [host port]}]
  (reset! server-config {:server {:host host :port port}}))

(defn start-system
  []
  (when-not @sys
    (timbre/info "Starting elin system")
    (let [config (e.config/load-config "." @server-config)
          system-map (e.system/new-system config)]
      (when-let [level (get-in config [:log :level])]
        (timbre/set-level! level))
      (reset! sys (component/start-system system-map)))
    ::started))

(defn stop-system
  []
  (when @sys
    (timbre/info "Stopping elin system")
    (component/stop-system @sys)
    (reset! sys nil)
    ::stopped))

(defn start
  []
  (start-system)
  (m.dev/start!))

(defn stop
  []
  (stop-system)
  (m.dev/stop!))

(defn go
  []
  (stop)
  (start))

(defn $ [& kws]
  (get-in @sys kws))
