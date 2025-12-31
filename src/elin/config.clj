(ns elin.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [elin.constant.project :as e.c.project]
   [elin.schema.config :as e.s.config]
   [elin.util.file :as e.u.file]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [taoensso.timbre :as timbre])
  (:import
   (java.net ServerSocket)))

(declare configure)

(defmethod aero/reader 'empty-port
  [_opts _tag _value]
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defmethod aero/reader 'spit-appender
  [_opts _tag value]
  (timbre/spit-appender value))

(defmethod aero/reader 'resource
  [_opts _tag value]
  (slurp (io/resource value)))

(defmethod aero/reader 'slurp
  [{::keys [base-dir]} _tag value]
  (let [absolute-path? (boolean (some->> base-dir
                                         (e.u.file/guess-file-separator)
                                         (str/starts-with? value)))]
    (try
      (cond->> value
        (not absolute-path?) (io/file base-dir)
        :always (slurp))
      (catch Exception _ nil))))

(defmethod aero/reader 're
  [_opts _tag value]
  (re-pattern value))

(def ^:private config-transformer
  (mt/transformer
    mt/default-value-transformer))

(m/=> merge-configs [:function
                     [:=> [:cat [:maybe map?] [:maybe map?]] map?]
                     [:=> [:cat [:maybe map?] [:maybe map?] [:* [:maybe map?]]] map?]])
(defn merge-configs
  ([c1 c2]
   (when (or c1 c2)
     (reduce-kv (fn [accm k v2]
                  (let [v1 (get accm k)]
                    (assoc accm k
                           (cond
                             (not v1)
                             v2

                             (and (contains? #{:includes :excludes :uses} k)
                                  (sequential? v1) (sequential? v2))
                             (vec (concat v1 v2))

                             (and (set? v1) (set? v2))
                             (set/union v1 v2)

                             (and (map? v1) (map? v2))
                             (merge-configs v1 v2)

                             :else
                             v2))))
                (or c1 {})
                c2)))
  ([c1 c2 & more-configs]
   (reduce merge-configs (or c1 {}) (cons c2 more-configs))))

(defn- configure-handler*
  [base-handler-config target-handler-config]
  (let [{:keys [includes excludes config-map]} target-handler-config
        exclude-set (set/union (set (or excludes []))
                               (set (or includes [])))
        has-config-map? (contains? base-handler-config :config-map)]
    (-> base-handler-config
        (merge-configs (assoc target-handler-config
                              :includes []
                              :excludes []))
        (update :includes #(-> (remove exclude-set %)
                               (concat includes)))
        (cond->
          has-config-map?
          (update :config-map (fn [base-config-map]
                                (reduce-kv
                                  (fn [accm handler-key base-handler-config-map]
                                    (assoc accm handler-key
                                      (if-let [target-handler-config-map (get config-map handler-key)]
                                        (configure base-handler-config-map target-handler-config-map)
                                        base-handler-config-map)))
                                  {}
                                  base-config-map)))))))

(defn- configure-interceptor*
  [base-interceptor-config target-interceptor-config]
  (let [{:keys [includes excludes]} target-interceptor-config
        exclude-set (set/union (set (or excludes []))
                               (set (or includes [])))]
    (-> base-interceptor-config
        (merge-configs (assoc target-interceptor-config
                              :includes []
                              :excludes []))
        (update :includes #(-> (remove exclude-set %)
                               (concat includes)
                               (distinct)))
        ;; Retain the :excludes settings to be able to exclude global interceptors
        (update :excludes #(-> (concat % excludes)
                               (distinct))))))

#_(m/=> expand-uses [:=> [:cat [:* [:cat symbol? map?]]]
                     [:map [:includes [:sequential symbol?]]
                      [:config-map [:map-of symbol? map?]]]])
(defn- expand-uses
  [uses]
  (->> (partition 2 uses)
       (reduce
         (fn [accm [k v]]
           (cond-> (update accm :includes conj k)
             (seq v)
             (update :config-map assoc k v)))
         {:includes [] :config-map {}})))

(defn configure-handler
  [base-handler-config target-handler-config]
  (configure-handler*
    base-handler-config
    (if-let [expanded (some-> (:uses target-handler-config)
                              (expand-uses))]
      (configure-handler* expanded (dissoc target-handler-config :uses))
      target-handler-config)))

(defn configure-interceptor
  [base-interceptor-config target-interceptor-config]
  (configure-interceptor*
    base-interceptor-config
    (if-let [expanded (some-> (:uses target-interceptor-config)
                              (expand-uses))]
      (configure-interceptor* expanded (dissoc target-interceptor-config :uses))
      target-interceptor-config)))

(defn expand-config
  [{:as config :keys [handler interceptor]}]
  (assoc config
         :handler (if-let [expanded (some-> (:uses handler)
                                            (expand-uses))]
                    (configure-handler expanded (dissoc handler :uses))
                    handler)
         :interceptor (if-let [expanded (some-> (:uses interceptor)
                                                (expand-uses))]
                        (configure-interceptor expanded (dissoc interceptor :uses))
                        interceptor)))

(defn configure
  [base-config
   target-config]
  (let [{base-handler :handler base-interceptor :interceptor} base-config
        {target-handler :handler target-interceptor :interceptor} target-config]
    (cond-> (merge-configs base-config
                           (dissoc target-config :handler :interceptor))
      (or base-handler target-handler)
      (update :handler #(configure-handler % (:handler target-config)))

      (or base-interceptor target-interceptor)
      (update :interceptor #(configure-interceptor % (:interceptor target-config))))))

(defn- load-default-config
  []
  (let [file (io/file (io/resource "config.edn"))
        base-dir (-> file
                     (.getParentFile)
                     (.getAbsolutePath))]
    (-> (aero/read-config file {::base-dir base-dir})
        (expand-config))))

(m/=> load-user-config [:-> map?])
(defn- load-user-config
  []
  (let [base-dir (e.u.file/get-config-directory)
        file (io/file base-dir "config.edn")]
    (or (when (.exists file)
          (-> (aero/read-config file {::base-dir base-dir})
              (expand-config)))
        {})))

(m/=> load-project-local-config [:-> string? map?])
(defn- load-project-local-config
  [dir]
  (let [config-dir-name (str "." e.c.project/name)
        file (some-> (e.u.file/find-file-in-parent-directories dir config-dir-name)
                     (io/file "config.edn"))]
    (or (when (and file (.exists file))
          (let [base-dir (-> file
                             (.getParentFile)
                             (.getAbsolutePath))]
            (-> (aero/read-config file {::base-dir base-dir})
                (expand-config))))
        {})))

(m/=> load-config [:-> string? map? e.s.config/?Config])
(defn load-config
  [dir server-config]
  (let [default-config (load-default-config)
        user-config (load-user-config)
        project-local-config (load-project-local-config dir)
        config (-> (merge-configs default-config server-config)
                   (configure user-config)
                   (configure project-local-config))]
    (m/coerce e.s.config/?Config
              config
              config-transformer)))

(m/=> lint-config [:=> [:cat string?] [:maybe map?]])
(defn lint-config
  [dir]
  (let [dummy-server-config {:plugin {:edn-files []}
                             :env {:cwd dir}
                             :server {:host "dummy" :port 12345}}
        conf (load-config dir dummy-server-config)]
    (-> (m/explain e.s.config/?ConfigLinter conf)
        (me/humanize))))

(comment
  (def config (load-config "." {:server {:host "" :port 0}
                                :env {:cwd "."}})))
