(ns elin.core
  (:require
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.system :as e.system]
   [taoensso.timbre :as timbre]))

(defn -main
  [json-config]
  (let [{:as config :keys [env]} (json/parse-string json-config keyword)
        config (e.config/load-config (:cwd env) config)
        sys-map (e.system/new-system config)]

    (when-let [level (get-in config [:log :level])]
      (timbre/set-level! level))

    (timbre/debug "elin.core Starting server:" (pr-str config) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))
