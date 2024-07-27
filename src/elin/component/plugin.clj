(ns elin.component.plugin
  (:require
   [babashka.classpath :as b.classpath]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
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

(m/=> load-plugins [:=> [:cat e.s.component/?LazyHost [:sequential string?]] e.s.plugin/?Plugin])
(defn- load-plugins
  [lazy-host edn-files]
  (loop [[edn-file & rest-edn-files] edn-files
         loaded-files #{}
         result {:name (str ::plugin)
                 :handlers []
                 :interceptors []}]
    (cond
      (not edn-file)
      result

      (contains? loaded-files edn-file)
      (recur rest-edn-files loaded-files result)

      :else
      (let [{:as content :keys [handlers interceptors]} (load-plugin lazy-host edn-file)
            loaded-files' (cond-> loaded-files
                            content
                            (conj edn-file))
            result' (if content
                      (-> result
                          (update :handlers concat (or handlers []))
                          (update :interceptors concat (or interceptors [])))
                      result)]
        (recur rest-edn-files loaded-files' result')))))

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
    (assoc this :loaded-plugin (load-plugins lazy-host (or edn-files []))))

  (stop [this]
    (timbre/info "Plugin component: Stopped")
    (dissoc this :loaded-plugin)))

(defn new-plugin
  [config]
  (map->Plugin (or (:plugin config) {})))
