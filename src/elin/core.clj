(ns elin.core
  (:require
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.log :as e.log]
   [elin.system :as e.system]))

(defn -main
  [host port current-working-directory]
  (let [port (Long/parseLong port)
        config (->> {:server {:host host :port port}}
                    (e.config/load-config current-working-directory))
        sys-map (e.system/new-system config)]

    (when-let [level (get-in config [:log :level])]
      (e.log/set-level! level))

    (e.log/debug "elin.core Starting server:" (pr-str config) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))
