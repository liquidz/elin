(ns elin.util.param
  (:require
   [malli.core :as m]
   [malli.error :as m.error]))

(defn parse [?schema value]
  (let [ret (m/parse ?schema value)]
    (if (= ::m/invalid ret)
      [nil (m.error/humanize (m/explain ?schema value))]
      [ret])))
