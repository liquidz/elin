(ns elin.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [elin.constant.project :as e.c.project]
   [elin.schema.config :as e.s.config]
   [elin.util.file :as e.u.file]
   [malli.core :as m]
   [malli.transform :as mt])
  (:import
   java.net.ServerSocket))

(defmethod aero/reader 'empty-port
  [_opts _tag _value]
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

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
                  (if-let [v1 (get accm k)]
                    (case k
                      (:includes :excludes)
                      (assoc accm k (if (and (sequential? v1) (sequential? v2))
                                      (vec (concat v1 v2))
                                      v2))

                      (assoc accm k (if (and (map? v1) (map? v2))
                                      (merge-configs v1 v2)
                                      v2)))
                    (assoc accm k v2)))
                (or c1 {})
                c2)))
  ([c1 c2 & more-configs]
   (reduce merge-configs (or c1 {}) (cons c2 more-configs))))

(m/=> load-config [:=> [:cat string? map?] e.s.config/?Config])
(defn load-config
  [dir server-config]
  (let [default (aero/read-config (io/resource "config.edn"))
        config (some-> (e.u.file/find-file-in-parent-directories dir e.c.project/config-file-name)
                       (aero/read-config))
        config (merge-configs server-config
                              default
                              (or config {}))]
    (m/coerce e.s.config/?Config
              config
              config-transformer)))
