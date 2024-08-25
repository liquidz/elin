(ns elin.interceptor.optional.evaluate
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.storage :as e.p.storage]
   [elin.util.interceptor :as e.u.interceptor]))

(def wrap-eval-code-interceptor
  {:name ::wrap-eval-code-interceptor
   :kind e.c.interceptor/evaluate
   :optional true
   :params ["identity"]
   :enter (fn [{:as ctx :keys [code]}]
            (let [{:keys [params]} (e.u.interceptor/self ctx)]
              (assoc ctx :code (format "(%s %s)" (first params) code))))})

(def eval-with-context-interceptor
  {:name ::eval-with-context-interceptor
   :kind e.c.interceptor/evaluate
   :optional true
   :enter (fn [{:as ctx :elin/keys [kind] :component/keys [host session-storage] :keys [code]}]
            (let [last-context (or (e.p.storage/get session-storage kind)
                                   "")
                  context (async/<!! (e.p.host/input! host "Evaluation context (let-style): " last-context))]
              (if (seq context)
                (do (e.p.storage/set session-storage kind context)
                    (assoc ctx :code (format "(clojure.core/let [%s] %s)"
                                             context code)))
                ctx)))})
