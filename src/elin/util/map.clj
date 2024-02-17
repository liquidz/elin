(ns elin.util.map
  (:require
   [malli.core :as m]))

(m/=> select-keys-by-namespace [:=> [:cat map? keyword?] map?])
(defn select-keys-by-namespace
  [m key-ns-kw]
  (reduce-kv (fn [accm k v]
               (if (= key-ns-kw (keyword (namespace k)))
                 (assoc accm k v)
                 accm))
             {} m))
