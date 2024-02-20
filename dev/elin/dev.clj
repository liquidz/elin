(ns elin.dev
  (:require
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.log :as e.log]
   [elin.system :as e.system]
   [malli.dev :as m.dev]))

(def system-map (atom nil))
(defonce sys (atom nil))

(defn initialize
  [{:keys [host port]}]
  (let [config (e.config/load-config "." {:server {:host host :port port}})]
    (reset! system-map (e.system/new-system config))))

(defn start-system
  []
  (when-not @sys
    (e.log/info "Starting elin system")
    (reset! sys (component/start-system @system-map))
    ::started))

(defn stop-system
  []
  (when @sys
    (e.log/info "Stopping elin system")
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
