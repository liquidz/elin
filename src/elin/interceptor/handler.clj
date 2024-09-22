(ns elin.interceptor.handler
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.message :as e.message]
   [exoscale.interceptor :as ix]))

(def handling-error-interceptor
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (e.message/error host (ex-message response)))
              (ix/when (comp e/error? :response))
              (ix/discard))})
