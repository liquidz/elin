(ns elin.util.interceptor
  (:require
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as-alias interceptor]
   [malli.core :as m]))

(m/=> self [:=> [:cat map?] [:maybe e.s.interceptor/?Interceptor]])
(defn self [context]
  (some-> context
          (get ::interceptor/stack)
          (first)))
