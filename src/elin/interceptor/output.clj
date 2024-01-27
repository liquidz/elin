(ns elin.interceptor.output
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.rpc :as e.p.rpc]))

(def print-output-interceptor
  {:name ::print-output-interceptor
   :kind e.c.interceptor/output
   :enter (fn [{:as ctx :keys [writer output]}]
            (e.p.rpc/echo-message writer (pr-str output) "ErrorMsg")
            ctx)})
