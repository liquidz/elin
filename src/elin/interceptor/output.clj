(ns elin.interceptor.output
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim.info-buffer :as e.f.v.info-buffer]
   [exoscale.interceptor :as ix]))

(def print-output-interceptor
  {:name ::print-output-interceptor
   :kind e.c.interceptor/output
   :enter (-> (fn [{:component/keys [host] :keys [output]}]
                (e.f.v.info-buffer/append host (format ";; %s\n%s" (:type output) (:text output))))
              (ix/discard))})
