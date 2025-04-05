(ns elin.interceptor.handler.evaluate
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(def hook
  "Evaluate any code before and after the handler's processing.

  .Configuration
  [%autowidth.stretch]
  |===
  | key | type | description

  | before | list / string | Code to be evaluated before the handler's processing.
  | after | list / string | Code to be evaluated after the handler's processing.
  |=== "
  {:kind e.c.interceptor/handler
   :enter (-> (fn [ctx]
                (when-let [code (:before (e.u.interceptor/config ctx #'hook))]
                  (e.f.evaluate/evaluate-code ctx (str code) {})))
              (ix/discard))
   :leave (-> (fn [ctx]
                (when-let [code (:after (e.u.interceptor/config ctx #'hook))]
                  (e.f.evaluate/evaluate-code ctx (str code) {})))
              (ix/discard))})
