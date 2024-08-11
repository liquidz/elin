(ns elin.dev
  (:require
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.schema.handler :as e.s.handler]
   [elin.system :as e.system]
   [malli.core :as m]
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
      (when-let [log-config (:log config)]
        (timbre/merge-config! log-config))
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

(m/=> elin [:function
            [:=> :cat e.s.handler/?Elin]
            [:=> [:cat map?] e.s.handler/?Elin]])
(defn elin
  ([]
   (elin {}))
  ([m]
   (let [kws [:nrepl :host :interceptor :session-storage :clj-kondo]]
     (merge
      {:message {:host "repl"
                 :message []}}
      (zipmap (map #(keyword "component" (name %)) kws)
              (map #($ (if (= :host %) :lazy-host %)) kws))
      m))))
