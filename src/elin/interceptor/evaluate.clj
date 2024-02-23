(ns elin.interceptor.evaluate
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim.virtual-text :as e.f.v.virtual-text]
   [elin.protocol.rpc :as e.p.rpc]
   [exoscale.interceptor :as ix]))

(def output-eval-result-to-cmdline-interceptor
  {:name ::output-eval-result-to-cmdline-interceptor
   :kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.p.rpc/echo-text host (str/trim (str v))))

                (when-let [v (:err response)]
                  (e.p.rpc/echo-message host (str/trim (str v)) "ErrorMsg")))
              (ix/discard))})

(def set-eval-result-to-virtual-text-interceptor
  {:name ::set-eval-result-to-virtual-text-interceptor
   :kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.f.v.virtual-text/set host
                                          (str v)
                                          {:highlight "DiffText"})))
              (ix/discard))})
