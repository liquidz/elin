(ns elin.interceptor.output
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [exoscale.interceptor :as ix]))

(def print-output-interceptor
  {:name ::print-output-interceptor
   :kind e.c.interceptor/output
   :enter (-> (fn [{:component/keys [host] :keys [output]}]
                (e.p.host/append-to-info-buffer host (format ";; %s\n%s" (:type output) (:text output))))
              (ix/discard))})
