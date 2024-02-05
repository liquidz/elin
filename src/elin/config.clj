(ns elin.config
  (:require
   [aero.core :as aero]
   [elin.schema.config :as e.s.config]
   [elin.util.file :as e.u.file]
   [malli.core :as m]
   [malli.transform :as mt]
   [medley.core :as medley]))

(def ^:private config-file-name
  ".elin.edn")

(def ^:private config-transformer
  (mt/transformer
   mt/default-value-transformer))

(m/=> load-config [:function
                   [:=> [:cat string?] e.s.config/?Config]
                   [:=> [:cat string? map?] e.s.config/?Config]])
(defn load-config
  ([dir]
   (load-config dir {}))
  ([dir base]
   (let [config (some-> (e.u.file/find-file-in-parent-directories dir config-file-name)
                        (aero/read-config))
         config (medley/deep-merge base
                                   (or config {}))]
     (m/coerce e.s.config/?Config
               config
               config-transformer))))
