(ns elin.util.server
  (:refer-clojure :exclude [format])
  (:require
   [clojure.walk :as walk]))

(defn format
  "Format data to be handled on the host side"
  [x]
  (cond
    (keyword? x)
    (str (symbol x))

    (symbol? x)
    (str x)

    (or (sequential? x)
        (map? x))
    (walk/prewalk #(cond
                     (keyword? %)
                     (str (symbol %))

                     (symbol? %)
                     (str %)

                     :else
                     %)
                  x)

    :else
    x))

(defn unformat
  "Format data to be handled on the elin server side"
  [x]
  (cond
    (sequential? x)
    (map unformat x)

    (map? x)
    (reduce-kv (fn [accm k v]
                 (assoc accm (keyword k) (unformat v)))
               {} x)

    :else
    x))
