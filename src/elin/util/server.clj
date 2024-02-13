(ns elin.util.server
  (:refer-clojure :exclude [format])
  (:require
   [clojure.walk :as walk]))

(defn format
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
