(ns elin.core
  (:require
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.log :as e.log]
   [elin.system :as e.system]))

(defn -main
  [json-config]
  (let [{:as config :keys [env]} (json/parse-string json-config keyword)
        config (e.config/load-config (:cwd env) config)
        sys-map (e.system/new-system config)]

    (when-let [level (get-in config [:log :level])]
      (e.log/set-level! level))

    (e.log/debug "elin.core Starting server:" (pr-str config) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))
