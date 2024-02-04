(ns elin.component.plugin
  (:require
   [babashka.classpath :as b.classpath]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.function.vim :as e.f.vim]
   [elin.log :as e.log]
   [elin.protocol.handler :as e.p.handler]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.plugin :as e.s.plugin]
   [malli.core :as m]
   [malli.error :as m.error]))

(defn- validation-error
  [edn-content]
  (some->> edn-content
           (m/explain e.s.plugin/?Plugin)
           (m.error/humanize)))

(defn- add-classpaths!
  [elin-plugin-edn-paths]
  (->> elin-plugin-edn-paths
       (map #(.getAbsolutePath (.getParentFile (io/file %))))
       (str/join ":")
       (b.classpath/add-classpath)))

(defrecord Plugin
  [handler lazy-writer interceptor]
  component/Lifecycle
  (start [this]
    (async/go
      (try
        (let [paths (async/<! (e.f.vim/call lazy-writer "elin#internal#plugin#search" []))]
          (add-classpaths! paths)
          (reduce
           (fn [loaded path]
             (let [content (edn/read-string (slurp path))
                   err (validation-error content)]
               (cond
                 err
                 (do (e.log/error lazy-writer "Invalid plugin.edn: " (pr-str err))
                     loaded)

                 (contains? loaded (:name content))
                 (do (e.log/warning lazy-writer "Already loaded plugin:" (:name content))
                     loaded)

                 :else
                 (let [{:keys [name handlers interceptors]} content]
                   (e.p.handler/add-handlers! handler handlers)
                   (e.p.interceptor/add-interceptors! interceptor interceptors)
                   (e.log/debug lazy-writer "Plugin loaded: " name)
                   (conj loaded name)))))
           #{} paths))
        (catch Exception ex
          (e.log/error lazy-writer "Failed to load plugins" ex))))
    this)
  (stop [this]
    this))

(defn new-plugin
  [_]
  (map->Plugin {}))
