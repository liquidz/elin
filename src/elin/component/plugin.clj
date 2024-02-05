(ns elin.component.plugin
  (:require
   [babashka.classpath :as b.classpath]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.log :as e.log]
   [elin.schema.plugin :as e.s.plugin]
   [elin.schema.server :as e.s.server]
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

(m/=> load-plugin [:=> [:cat e.s.server/?Writer string?] [:maybe e.s.plugin/?Plugin]])
(defn- load-plugin
  [lazy-writer edn-file]
  (let [content (edn/read-string (slurp edn-file))
        err (validation-error content)]
    (if err
      (e.log/error lazy-writer "Invalid plugin.edn: " (pr-str err))
      content)))

(m/=> load-plugins [:=> [:cat e.s.server/?Writer [:sequential string?]] e.s.plugin/?Plugin])
(defn- load-plugins
  [lazy-writer edn-files]
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
      (let [{:as content :keys [handlers interceptors]} (load-plugin lazy-writer edn-file)
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
  [lazy-writer edn-files loaded-plugin]
  component/Lifecycle
  (start [this]
    (add-classpaths! edn-files)
    (assoc this :loaded-plugin (load-plugins lazy-writer edn-files)))

  (stop [this]
    (dissoc this :loaded-plugin)))

(defn new-plugin
  [config]
  (map->Plugin (or (:plugin config) {})))
