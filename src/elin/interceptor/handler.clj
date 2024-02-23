(ns elin.interceptor.handler
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.protocol.rpc :as e.p.rpc]
   [exoscale.interceptor :as ix]))

(def handling-error-interceptor
  {:name ::handling-error-interceptor
   :kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (e.p.rpc/echo-message host (ex-message response) "ErrorMsg"))
              (ix/when (comp e/error? :response))
              (ix/discard))})
