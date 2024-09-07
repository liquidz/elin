(ns elin.interceptor.debug
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as ix]
   [malli.core :as m]
   [malli.error :as m.error]
   [taoensso.timbre :as timbre]))

(def ^:private do-not-log-ops
  #{"completions" "complete"})

(def nrepl-debug-interceptor
  {:name ::nrepl-debug-interceptor
   :kind e.c.interceptor/nrepl
   :enter (-> (fn [{:keys [request]}]
                (timbre/debug "Nrepl >>>" (pr-str request)))
              (ix/when #(not (contains? do-not-log-ops (get-in % [:request :op]))))
              (ix/discard))
   :leave (-> (fn [{:keys [response]}]
                (timbre/debug "Nrepl <<<" (pr-str response)))
              (ix/when #(not (contains? do-not-log-ops (get-in % [:request :op]))))
              (ix/discard))})

(def ^:private kind-schema-map
  {e.c.interceptor/all any?
   e.c.interceptor/autocmd e.s.interceptor/?AutocmdContext
   e.c.interceptor/connect e.s.interceptor/?ConnectContext
   e.c.interceptor/evaluate e.s.interceptor/?EvaluateContext
   e.c.interceptor/handler e.s.interceptor/?HandlerContext
   e.c.interceptor/nrepl e.s.interceptor/?NreplContext
   e.c.interceptor/raw-nrepl e.s.interceptor/?RawNreplContext
   e.c.interceptor/output e.s.interceptor/?OutputContext
   e.c.interceptor/test e.s.interceptor/?TestContext
   e.c.interceptor/quickfix e.s.interceptor/?QuickfixContext})

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

(def tap-interceptor
  "TODO remove-tap"
  {:name ::tap-interceptor
   :kind e.c.interceptor/connect
   :leave (-> (fn [_]
                (add-tap #(timbre/error "Debug tap:" %)))
              (ix/discard))})

(def initialize-debugger-interceptor
  {:name ::initialize-debugger-interceptor
   :kind e.c.interceptor/connect
   :leave (-> (fn [{:component/keys [nrepl]}]
                (when (e.p.nrepl/supported-op? nrepl e.c.nrepl/init-debugger-op)
                  (e.f.n.cider/init-debugger nrepl)))
              (ix/discard))})
