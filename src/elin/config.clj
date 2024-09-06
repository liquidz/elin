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

                             (and (contains? #{:includes :excludes} k)
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

(m/=> load-user-config [:-> map?])
(defn- load-user-config
  []
  (let [file (io/file (e.u.file/get-config-directory) "config.edn")]
    (or (when (.exists file)
          (aero/read-config file))
        {})))

(m/=> load-project-local-config [:-> string? map?])
(defn- load-project-local-config
  [dir]
  (or (some-> (e.u.file/find-file-in-parent-directories dir e.c.project/config-file-name)
              (aero/read-config))
      {}))

(m/=> load-config [:-> string? map? e.s.config/?Config])
(defn load-config
  [dir server-config]
  (let [default-config (aero/read-config (io/resource "config.edn"))
        user-config (load-user-config)
        project-local-config (load-project-local-config dir)
        config (merge-configs server-config
                              default-config
                              user-config
                              project-local-config)]
    (m/coerce e.s.config/?Config
              config
              config-transformer)))
