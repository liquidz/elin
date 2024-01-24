(ns elin.interceptor.nrepl
  (:require
   [elin.log :as e.log]))

(def debug-interceptor
  {:name ::debug-interceptor
   :enter (fn [{:as ctx :keys [request]}]
            (e.log/debug "Nrepl >>>" (pr-str request))
            ctx)
   :leave (fn [{:as ctx :keys [response]}]
            (e.log/debug "Nrepl <<<" (pr-str response))
            ctx)})
