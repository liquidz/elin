(ns elin.interceptor.optional.evaluate
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.util.interceptor :as e.u.interceptor]))

(def wrap-eval-code-interceptor
  {:name ::wrap-eval-code-interceptor
   :kind e.c.interceptor/evaluate
   :optional true
   :params ["identity"]
   :enter (fn [{:as ctx :keys [code]}]
            (let [{:keys [params]} (e.u.interceptor/self ctx)]
              (assoc ctx :code (format "(%s %s)" (first params) code))))})
