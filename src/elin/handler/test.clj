(ns elin.handler.test
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.nrepl.test :as e.f.n.test]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.function.storage.test :as e.f.s.test]
   [elin.handler.evaluate :as e.h.evaluate]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
   [malli.core :as m]))

(m/=> run-test-under-cursor [:=> [:cat e.s.handler/?Elin] any?])
(defn run-test-under-cursor
  "Run test under cursor."
  [{:as elin :component/keys [interceptor session-storage]}]
  (e/let [{:keys [options]} (e.f.evaluate/get-var-name-from-current-top-list elin)
          {ns-str :ns var-name :var-name} options
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
            (e.f.s.test/set-last-test-query session-storage {:ns (:ns ctx)
                                                             :vars (:vars ctx)
                                                             :current-file (:file ctx)
                                                             :base-line (:line ctx)})
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
  "Run test in current namespace."
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
    (e.h.evaluate/evaluate-current-buffer elin)

    (e.p.interceptor/execute
      interceptor e.c.interceptor/test context
      (fn [{:as ctx :component/keys [nrepl]}]
        (if (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
          ;; cider-nrepl
          (let [query {:ns-query {:exactly [(:ns ctx)]}}]
            (e.f.s.test/set-last-test-query session-storage {:ns (:ns ctx)
                                                             :vars []
                                                             :current-file (:file ctx)
                                                             :base-line (:line ctx)})
            (assoc ctx :response (e.f.n.cider/test-var-query!! nrepl query)))
          ;; plain
          (let [vars' `(vals (ns-interns '~(symbol (:ns ctx))))
                query {:ns (:ns ctx)
                       :vars vars'
                       :current-file (:file ctx)
                       :base-line (:line ctx)}]
            (e.f.s.test/set-last-test-query session-storage query)
            (assoc ctx :response (e.f.n.test/test-var-query!! nrepl query))))))))

(defn- run-tests-by-query
  [{:as elin :component/keys [interceptor]} query]
  (let [context (-> (e.u.map/select-keys-by-namespace elin :component)
                    (assoc :ns (or (:ns query) "")
                           :line (or (:base-line query) 0)
                           :column 0
                           :file (or (:current-file query) "")
                           :vars (or (map str (:vars query)) [])))]
    (e.p.interceptor/execute
      interceptor e.c.interceptor/test context
      (fn [{:as ctx :component/keys [nrepl]}]
        (let [test-var-query-supported? (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
              query (if test-var-query-supported?
                      (cond-> {}
                        (:ns ctx)
                        (assoc :ns-query {:exactly [(:ns ctx)]})

                        (:vars ctx)
                        (assoc :exactly (:vars ctx)))
                      {:ns (:ns ctx)
                       :vars (map symbol (:vars ctx))
                       :current-file (:file ctx)
                       :base-line (:line ctx)})
              resp (if test-var-query-supported?
                     (e.f.n.cider/test-var-query!! nrepl query)
                     (e.f.n.test/test-var-query!! nrepl query))]
          (assoc ctx :response resp))))))

(defn rerun-last-tests
  "Rerun last tests."
  [{:as elin :component/keys [session-storage]}]
  (->> (e.f.s.test/get-last-test-query session-storage)
       (run-tests-by-query elin)))

(m/=> rerun-last-failed-tests [:=> [:cat e.s.handler/?Elin] any?])
(defn rerun-last-failed-tests
  "Rerun last failed tests."
  [{:as elin :component/keys [host session-storage]}]
  (let [query (e.f.s.test/get-last-failed-tests-query session-storage)]
    (if (and query
             (seq (:vars query)))
      (run-tests-by-query elin query)
      (e.message/warning host "There are no failed tests to rerun."))))
