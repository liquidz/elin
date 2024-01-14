(ns elin.util.interceptor
  (:require
   [exoscale.interceptor :as interceptor]))

(defn terminator
  [f]
  {:name ::terminator
   :enter f})

(defn execute
  [context interceptors terminator]
  (let [terminator' {:name ::terminator
                     :enter terminator}]
    (interceptor/execute context (concat interceptors [terminator']))))
