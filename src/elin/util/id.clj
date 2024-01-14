(ns elin.util.id
  (:require
   [malli.core :as m]))

(def ^:private id-atom (atom 0))

(m/=> next-id [:=> :cat int?])
(defn- next-id
  []
  (let [id (swap! id-atom inc)]
    (when (> id 10000)
      (reset! id-atom 0))
    id))
