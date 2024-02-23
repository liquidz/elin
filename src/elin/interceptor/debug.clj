(ns elin.interceptor.debug
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.log :as e.log]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as ix]
   [malli.core :as m]
   [malli.error :as m.error]))

(def nrepl-debug-interceptor
  {:name ::nrepl-debug-interceptor
   :kind e.c.interceptor/nrepl
   :enter (-> (fn [{:keys [request]}]
                (e.log/debug "Nrepl >>>" (pr-str request)))
              (ix/discard))
   :leave (-> (fn [{:keys [response]}]
                (e.log/debug "Nrepl <<<" (pr-str response)))
              (ix/discard))})

(def ^:private kind-schema-map
  {e.c.interceptor/all any?
   e.c.interceptor/autocmd e.s.interceptor/?AutocmdContext
   e.c.interceptor/connect e.s.interceptor/?ConnectContext
   e.c.interceptor/evaluate e.s.interceptor/?EvaluateContext
   e.c.interceptor/handler e.s.interceptor/?HandlerContext
   e.c.interceptor/nrepl e.s.interceptor/?NreplContext
   e.c.interceptor/output e.s.interceptor/?OutputContext
   e.c.interceptor/test e.s.interceptor/?TestContext})

(def interceptor-context-checking-interceptor
  {:name ::interceptor-context-checking-interceptor
   :kind e.c.interceptor/all
   :enter (fn [{:as ctx :elin/keys [kind]}]
            (if-let [schema (get kind-schema-map kind)]
              (do
                (when-let [err (some->> ctx
                                        (m/explain schema)
                                        (m.error/humanize))]
                  (throw (ex-info (format "Invalid context for %s: %s"
                                          kind
                                          err)
                                  err)))
                ctx)
              (throw (ex-info "Unknown kind" {:kind kind :context ctx}))))})
