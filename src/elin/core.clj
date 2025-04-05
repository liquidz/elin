(ns elin.core
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.system :as e.system]
   [taoensso.timbre :as timbre]))

(defn- elin-symbol-string?
  [v]
  (and (string? v)
       (str/starts-with? v "elin.")))

(defn- key-fn [v]
  (if (elin-symbol-string? v)
    (symbol v)
    (keyword v)))

(defn- symbol-fn [v]
  (if (elin-symbol-string? v)
    (symbol v)
    v))

(defn- json-parse-string
  [s]
  (->> (json/parse-string s key-fn)
       (walk/prewalk symbol-fn)))

(defn -main
  [json-config]
  (let [{:as config :keys [env]} (json-parse-string json-config)
        config (e.config/load-config (:cwd env) config)
        sys-map (e.system/new-system config)]

    (when-let [log-config (:log config)]
      (timbre/merge-config! log-config))

    (timbre/debug "elin.core Starting server:" (pr-str config) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))
