(ns elin.component.plugin
  (:require
   [babashka.classpath :as b.classpath]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.message :as e.message]
   [elin.schema.component :as e.s.component]
   [elin.schema.plugin :as e.s.plugin]
   [malli.core :as m]
   [malli.error :as m.error]
   [taoensso.timbre :as timbre]))

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

(m/=> load-plugin [:=> [:cat e.s.component/?LazyHost string?] [:maybe e.s.plugin/?Plugin]])
(defn- load-plugin
  [lazy-host edn-file]
  (let [content (edn/read-string (slurp edn-file))
        err (validation-error content)]
    (if err
      (e.message/warning lazy-host "Invalid plugin.edn: " (pr-str err))
      content)))

(m/=> read-plugins [:=> [:cat e.s.component/?LazyHost [:sequential string?]] [:sequential e.s.plugin/?Plugin]])
(defn- read-plugins
  [lazy-host edn-files]
  (loop [[edn-file & rest-edn-files] edn-files
         loaded-files #{}
         result []]
    (cond
      (not edn-file)
      result

      (contains? loaded-files edn-file)
      (recur rest-edn-files loaded-files result)

      :else
      (let [content (load-plugin lazy-host edn-file)
            loaded-files' (cond-> loaded-files
                            content
                            (conj edn-file))
            result' (conj result content)]
        (recur rest-edn-files loaded-files' result')))))

(m/=> unify-plugins [:-> [:sequential e.s.plugin/?Plugin] e.s.plugin/?Plugin])
(defn- unify-plugins
  [plugins]
  (reduce
    (fn [accm {:keys [export]}]
      (cond-> accm
        export
        (update :export e.config/merge-configs export)))
    {:name (str ::plugin)}
    plugins))

(defrecord Plugin
  [;; COMPONENTS
   lazy-host
   ;; CONFIGS
   edn-files
   ;; PARAMS
   loaded-plugin]

  component/Lifecycle
  (start [this]
    (add-classpaths! edn-files)
    (timbre/info "Plugin component: Started")
    (let [loaded-plugin (-> (read-plugins lazy-host (or edn-files []))
                            (unify-plugins))]
      (assoc this :loaded-plugin loaded-plugin)))

  (stop [this]
    (timbre/info "Plugin component: Stopped")
    (dissoc this :loaded-plugin)))

(defn new-plugin
  [config]
  (map->Plugin (or (:plugin config) {})))
