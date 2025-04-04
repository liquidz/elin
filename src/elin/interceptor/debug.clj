(ns elin.interceptor.debug
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.popup :as e.f.popup]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.interceptor :as e.s.interceptor]
   [elin.util.nrepl :as e.u.nrepl]
   [elin.util.sexpr :as e.u.sexpr]
   [exoscale.interceptor :as ix]
   [malli.core :as m]
   [malli.error :as m.error]
   [taoensso.timbre :as timbre]))

(def ^:private do-not-log-ops
  #{e.c.nrepl/completions-op
    e.c.nrepl/complete-op
    e.c.nrepl/log-search})

(def nrepl-debug
  {:kind e.c.interceptor/nrepl
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
   e.c.interceptor/disconnect e.s.interceptor/?DisconnectContext
   e.c.interceptor/evaluate e.s.interceptor/?EvaluateContext
   e.c.interceptor/handler e.s.interceptor/?HandlerContext
   e.c.interceptor/nrepl e.s.interceptor/?NreplContext
   e.c.interceptor/raw-nrepl e.s.interceptor/?RawNreplContext
   e.c.interceptor/output e.s.interceptor/?OutputContext
   e.c.interceptor/test e.s.interceptor/?TestContext
   e.c.interceptor/test-result e.s.interceptor/?TestResultContext
   e.c.interceptor/quickfix e.s.interceptor/?QuickfixContext
   e.c.interceptor/modify-code e.s.interceptor/?ModifyCodeContext
   e.c.interceptor/tap e.s.interceptor/?TapContext
   e.c.interceptor/http-route e.s.interceptor/?HttpRouteContext
   e.c.interceptor/http-request e.s.interceptor/?HttpRequestContext
   e.c.interceptor/configure-handler e.s.interceptor/?ConfigureHandlerContext})

(def interceptor-context-checking
  {:kind e.c.interceptor/all
   :enter (fn [{:as ctx :interceptor/keys [kind]}]
            (if-let [schema (get kind-schema-map kind)]
              (do
                (when-let [err (some->> ctx
                                        (m/explain schema)
                                        (m.error/humanize))]
                  (timbre/error "Invalid context" {:kind kind
                                                   :error err
                                                   :context (->> ctx
                                                                 (reduce-kv
                                                                   (fn [accm k v]
                                                                     (if (namespace k)
                                                                       accm
                                                                       (assoc accm k v)))
                                                                   {}))})
                  (throw (ex-info (format "Invalid context for %s: %s"
                                          kind
                                          err)
                                  err)))
                ctx)
              (throw (ex-info "Unknown kind" {:kind kind :context ctx}))))})

(def tap
  "TODO remove-tap"
  {:kind e.c.interceptor/connect
   :leave (-> (fn [_]
                (add-tap #(timbre/error "Debug tap:" %)))
              (ix/discard))})

(def initialize-debugger
  {:kind e.c.interceptor/connect
   :leave (-> (fn [{:component/keys [nrepl]}]
                (when (e.p.nrepl/supported-op? nrepl e.c.nrepl/init-debugger-op)
                  (e.f.n.cider/init-debugger nrepl)))
              (ix/discard))})

(def ^:private supported-input-types
  {"c" "continue"
   "l" "locals"
   ;; "i" "inspect"
   "t" "trace"
   "h" "here"
   "C" "continue-all"
   "n" "next"
   ;; "o" "out"
   ;; "i" "inject"
   "s" "stacktrace"
   ;; "i" "inspect-prompt"
   "q" "quit"})

(defn- generate-input-prompt
  [input-type]
  (let [reversed-supported-input-types (->> supported-input-types
                                            (map (fn [[k v]] [v k]))
                                            (into {}))
        supported (set (keys reversed-supported-input-types))]
    (->> input-type
         (filter supported)
         (map #(format "(%s)%s" (reversed-supported-input-types %) %))
         (str/join ", "))))
(comment (println (generate-debug-text sample)))

(defn- generate-debug-text
  [{:keys [debug-value locals]}]
  (let [max-key-len (->> locals
                         (map (comp count first))
                         (apply max))
        space #(->> (repeat (- max-key-len (count %)) " ")
                    (apply str))
        locals-str (->> locals
                        (map (fn [[k v]]
                               (str ":" k (space k) " " v)))
                        (str/join "\n"))]
    (->> [";; value "
          (str debug-value)
          ";; locals"
          locals-str]
         (str/join "\n"))))

;; Sample message
;; {:debug-value "2",
;;  :original-ns "core",
;;  :key "2f20d369-391a-410e-9482-e7bd8bb5c45a",
;;  :locals [["a" "2"]],
;;  :file "/Users/iizukamasashi/opt/foo/bar/src/core.clj",
;;  :column 1,
;;  :input-type ["continue" "locals" "inspect" "trace" "here" "continue-all" "next" "out" "inject" "stacktrace" "inspect-prompt" "quit" "in" "eval"],
;;  :prompt [],
;;  :coor [3 1],
;;  :line 10,
;;  :status ["need-debug-input"],
;;  :code "(defn- foo [a]\n  #dbg (+ a 1))",
;;  :original-id 15,
;;  :session "c64e3734-5813-47da-af8b-a1af053db19d"))
(def process-debugger
  {:kind e.c.interceptor/raw-nrepl
   :enter (-> (fn [{:as ctx :component/keys [nrepl host] :keys [message]}]
                (when (e.u.nrepl/has-status? message "need-debug-input")
                  (let [{:keys [line column coor input-type]} message
                        {base-code :code} (e.f.sexpr/get-list ctx (:line message) (:column message))
                        {:keys [code position]} (e.u.sexpr/apply-cider-coordination base-code coor)
                        highlight-line (+ line (first position))
                        highlight-start-col (+ column (second position))
                        highlight-end-col (+ highlight-start-col (dec (count code)))
                        popup-id (e.f.popup/open ctx (generate-debug-text message)
                                                 {:group "debugger"
                                                  :line (inc highlight-line)
                                                  :col highlight-start-col
                                                  :filetype "clojure"})
                        input-prompt  (generate-input-prompt input-type)]
                    (e.p.host/set-highlight host
                                            "Search"
                                            highlight-line
                                            highlight-start-col
                                            highlight-end-col)
                    ;; Wait for the highlight to be reflected
                    (async/<!! (async/timeout 1))
                    (loop []
                      (let [input (async/<!! (e.p.host/input! host
                                                              (str input-prompt ":\n")
                                                              ""))
                            input' (or (get supported-input-types input)
                                       input)]
                        (if (contains? (set (vals supported-input-types)) input')
                          (do (e.f.n.cider/debug-input nrepl (:key message) (str ":" input'))
                              (e.f.popup/close ctx popup-id)
                              (e.p.host/clear-highlight host))
                          (recur)))))))
              (ix/discard))})
