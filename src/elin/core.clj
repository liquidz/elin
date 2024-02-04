(ns elin.core
  (:require
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.log :as e.log]
   [elin.schema.config :as e.s.config]
   [elin.system :as e.system]
   [malli.core :as m]
   [malli.error :as m.error]))

(defn- parse-json-config
  [json-config]
  (let [res (json/parse-string json-config keyword)]
    (when-let [err (some->> res
                            (m/explain e.s.config/?Config)
                            (m.error/humanize))]
      (throw (ex-info "Invalid server config" err)))
    res))

(defn -main
  [json-config]
  (let [{:as config :keys [env]} (parse-json-config json-config)
        config (e.config/load-config (:cwd env) config)
        sys-map (e.system/new-system config)]

    (when-let [level (get-in config [:log :level])]
      (e.log/set-level! level))

    (e.log/debug "elin.core Starting server:" (pr-str config) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))
