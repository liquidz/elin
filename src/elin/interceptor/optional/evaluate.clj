(ns elin.interceptor.optional.evaluate
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.storage :as e.p.storage]
   [elin.util.interceptor :as e.u.interceptor]))

(def wrap-eval-code
  {:kind e.c.interceptor/evaluate
   :enter (fn [{:as ctx :keys [code]}]
            (let [config (e.u.interceptor/config ctx #'wrap-eval-code)]
              (if (seq (:code config))
                (assoc ctx :code (format "(%s %s)" (:code config) code))
                ctx)))})

(def eval-with-context
  {:kind e.c.interceptor/evaluate
   :enter (fn [{:as ctx :interceptor/keys [kind] :component/keys [host session-storage] :keys [code]}]
            (let [last-context (or (e.p.storage/get session-storage kind)
                                   "")
                  context (async/<!! (e.p.host/input! host "Evaluation context (let-style): " last-context))]
              (if (seq context)
                (do (e.p.storage/set session-storage kind context)
                    (assoc ctx :code (format "(clojure.core/let [%s] %s)"
                                             context code)))
                ctx)))})
