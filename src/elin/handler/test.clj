(ns elin.handler.test
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.nrepl.test :as e.f.n.test]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.function.storage.test :as e.f.s.test]
   [elin.handler.evaluate :as e.h.evaluate]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
   [malli.core :as m]
   [rewrite-clj.zip :as r.zip]))

(defn- extract-multi-method-name
  [code]
  (let [zloc (-> (r.zip/of-string code)
                 (r.zip/down))]
    (when (contains? #{'defmulti 'defmethod} (r.zip/sexpr zloc))
      (some-> zloc
              (r.zip/next)
              (r.zip/sexpr)
              (str)))))

(m/=> run-test-under-cursor [:=> [:cat e.s.handler/?Elin] any?])
(defn run-test-under-cursor
  [{:as elin :component/keys [interceptor session-storage]}]
  (e/let [{:keys [code response options]} (e.f.evaluate/evaluate-current-top-list elin)
          {ns-str :ns} options
          var-name (or (some->> (extract-multi-method-name code)
                                (str ns-str "/"))
                       (str/replace (:value response) #"^#'" ""))
          context (-> (e.u.map/select-keys-by-namespace elin :component)
                      (assoc :ns ns-str
                             :line (:line options)
                             :column (:column options)
                             :file (:file options)
                             :vars [var-name]))]
    (e.p.interceptor/execute
     interceptor e.c.interceptor/test context
     (fn [{:as ctx :component/keys [nrepl]}]
       (if (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
         ;; cider-nrepl
         (let [query {:ns-query {:exactly [(:ns ctx)]}
                      :exactly (:vars ctx)}]
           (e.f.s.test/set-last-test-query session-storage query)
           (assoc ctx :response (e.f.n.cider/test-var-query!! nrepl query)))

         ;; plain
         (let [vars' (->> (:vars ctx)
                          (mapv #(symbol (str "#'" %))))
               query {:ns (:ns ctx)
                      :vars vars'
                      :current-file (:file ctx)
                      :base-line (:line ctx)}]
           (e.f.s.test/set-last-test-query session-storage query)
           (assoc ctx :response (e.f.n.test/test-var-query!! nrepl query))))))))

(defn run-tests-in-ns
  [{:as elin :component/keys [host interceptor session-storage]}]
  (e/let [ns-str (e.f.sexpr/get-namespace elin)
          path (async/<!! (e.p.host/get-current-file-path! host))
          context (-> (e.u.map/select-keys-by-namespace elin :component)
                      (assoc :ns ns-str
                             :line 0
                             :column 0
                             :file path
                             :vars []))]
    ;; NOTE: Reload ns to match run-test-under-cursor's behavior
    (e.h.evaluate/load-current-file elin)

    (e.p.interceptor/execute
     interceptor e.c.interceptor/test context
     (fn [{:as ctx :component/keys [nrepl]}]
       (if (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
         ;; cider-nrepl
         (let [query {:ns-query {:exactly [(:ns ctx)]}}]
           (e.f.s.test/set-last-test-query session-storage query)
           (assoc ctx :response (e.f.n.cider/test-var-query!! nrepl query)))
         ;; plain
         (let [vars' `(vals (ns-interns '~(symbol (:ns ctx))))
               query {:ns (:ns ctx)
                      :vars vars'
                      :current-file (:file ctx)
                      :base-line (:line ctx)}]
           (e.f.s.test/set-last-test-query session-storage query)
           (assoc ctx :response (e.f.n.test/test-var-query!! nrepl query))))))))

(defn rerun-last-tests
  [{:as elin :component/keys [interceptor session-storage]}]
  (let [query (e.f.s.test/get-last-test-query session-storage)
        context (-> (e.u.map/select-keys-by-namespace elin :component)
                    (assoc :ns (or (:ns query) "")
                           :line (or (:base-line query) 0)
                           :column 0
                           :file (or (:current-file query) "")
                           :vars (or (map str (:vars query)) [])))]
    (e.p.interceptor/execute
     interceptor e.c.interceptor/test context
     (fn [{:as ctx :component/keys [nrepl]}]
       (let [query {:ns (:ns ctx)
                    :vars (map symbol (:vars ctx))
                    :current-file (:file ctx)
                    :base-line (:line ctx)}
             resp (if (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
                    (e.f.n.cider/test-var-query!! nrepl query)
                    (e.f.n.test/test-var-query!! nrepl query))]
         (assoc ctx :response resp))))))
