(ns elin.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [elin.constant.project :as e.c.project]
   [elin.schema.config :as e.s.config]
   [elin.util.file :as e.u.file]
   [malli.core :as m]
   [malli.transform :as mt]
   [taoensso.timbre :as timbre])
  (:import
   java.net.ServerSocket))

(defmethod aero/reader 'empty-port
  [_opts _tag _value]
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defmethod aero/reader 'spit-appender
  [_opts _tag value]
  #_{:clj-kondo/ignore [:unresolved-var]}
  (timbre/spit-appender value))

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


(defn configure-handler
  [base-handler-config target-handler-config]
  (let [{:keys [includes excludes]} target-handler-config
        exclude-set (set/union (set (or excludes []))
                               (set (or includes [])))]
    (-> base-handler-config
        (merge-configs (assoc target-handler-config
                              :includes []
                              :excludes []))
        (update :includes #(-> (remove exclude-set %)
                               (concat includes))))))

(defn configure-interceptor
  [base-interceptor-config target-interceptor-config]
  (let [{:keys [includes excludes]} target-interceptor-config
        exclude-set (set/union (set (or excludes []))
                               (set (or includes [])))]
    (-> base-interceptor-config
        (merge-configs (assoc target-interceptor-config
                              :includes []
                              :excludes []))
        (update :includes #(-> (remove exclude-set %)
                               (concat includes))))))

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
  [base-config target-config]
  (let [target-config' (expand-config target-config)]
    (-> base-config
        (merge-configs (dissoc target-config' :handler :interceptor))
        (update :handler #(configure-handler % (:handler target-config')))
        (update :interceptor #(configure-interceptor % (:interceptor target-config'))))))


(comment
  (configure {:handler {:includes ['foo]}}
             {:handler {:uses ['foo {:a 1}]}}))

(defn- load-default-config
  []
  (-> (io/resource "config.edn")
      (aero/read-config)
      (expand-config)))

(m/=> load-user-config [:-> map?])
(defn- load-user-config
  []
  (let [file (io/file (e.u.file/get-config-directory) "config.edn")]
    (or (when (.exists file)
          (-> (aero/read-config file)
              (expand-config)))
        {})))

(m/=> load-project-local-config [:-> string? map?])
(defn- load-project-local-config
  [dir]
  (let [config-dir-name (str "." e.c.project/name)
        file (some-> (e.u.file/find-file-in-parent-directories dir config-dir-name)
                     (io/file "config.edn"))]
    (or (when (and file (.exists file))
          (-> (aero/read-config file)
              (expand-config)))
        {})))

(m/=> load-config [:-> string? map? e.s.config/?Config])
(defn load-config
  [dir server-config]
  (let [default-config (load-default-config)
        user-config (load-user-config)
        project-local-config (load-project-local-config dir)
        config (-> server-config
                   (merge-configs default-config)
                   (configure user-config)
                   (configure project-local-config))]
    (m/coerce e.s.config/?Config
              config
              config-transformer)))

(comment
  (def config (load-config "." {:server {:host "" :port 0}
                                :env {:cwd "."}})))
