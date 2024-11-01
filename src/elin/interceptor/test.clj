(ns elin.interceptor.test
  (:require
   [clojure.core.async :as async]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.nrepl.cider.test :as e.f.n.c.test]
   [elin.function.quickfix :as e.f.quickfix]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.function.storage.test :as e.f.s.test]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.map :as e.u.map]
   [elin.util.sexpr :as e.u.sexpr]
   [exoscale.interceptor :as ix]
   [taoensso.timbre :as timbre]))

(def ^:private sign-name "error")

(defn- get-failed-tests-cider-nrepl-query
  [failed-results]
  {:ns-query {:exactly (map :ns failed-results)}
   :exactly (->> failed-results
                 (map #(format "%s/%s" (:ns %) (:var %))))})

(defn- get-failed-tests-plain-repl-query
  [failed-results]
  (let [failed-result (->> failed-results
                           (some #(and (:ns %) %)))
        vars (->> failed-results
                  (filter #(= (:ns failed-result) (:ns %)))
                  (map #(symbol (format "#'%s/%s" (:ns %) (:var %)))))]
    {:ns (:ns failed-result)
     :vars vars
     :current-file (:filename failed-result)
     :base-line 0}))

(defn- get-failed-tests-query
  [nrepl failed-results]
  (if (e.p.nrepl/supported-op? nrepl e.c.nrepl/test-var-query-op)
    (get-failed-tests-cider-nrepl-query failed-results)
    (get-failed-tests-plain-repl-query failed-results)))

(defn- generate-quickfix-text
  [result]
  (cond-> (format "%s/%s" (:ns result) (:var result))
    (seq (:text result))
    (str ": " (:text result))))

(defn pprint-str
  [s]
  (when s
    (try
      (with-out-str
        (-> (read-string s)
            (pp/pprint)))
      (catch Exception _
        s))))

(def parse-test-result
  {:kind e.c.interceptor/test
   :leave (fn [{:as ctx :component/keys [nrepl interceptor] :keys [response]}]
            (let [{:keys [passed failed]} (->> (e.f.n.c.test/collect-results nrepl response)
                                               (group-by :result))
                  {:keys [succeeded? summary]} (e.f.n.c.test/summary response)]
              (-> ctx
                  (assoc :passed passed
                         :failed failed
                         :succeeded? succeeded?
                         :summary summary)
                  (->> (e.p.interceptor/execute interceptor e.c.interceptor/test-result)))))})

(def update-test-result-sign
  {:kind e.c.interceptor/test-result
   :enter (-> (fn [{:component/keys [host] :keys [passed failed]}]
                ;; unsign
                (if (seq passed)
                  (doseq [var-str (distinct (map :var passed))]
                    (e.p.host/unplace-signs-by host {:name sign-name :group var-str}))
                  (e.p.host/unplace-signs-by host {:name sign-name :group "*"}))
                ;; sign
                (doseq [{:as result :keys [lnum]} failed
                        :when lnum]
                  (e.p.host/place-sign host {:name sign-name
                                             :lnum lnum
                                             :file (:filename result)
                                             :group (:var result)})))
              (ix/discard))})

(def append-test-result-to-info-buffer
  {:kind e.c.interceptor/test-result
   :enter (-> (fn [{:component/keys [host] :keys [failed summary]}]
                (let [s (->> failed
                             (mapcat (fn [{:as failed-result :keys [text lnum expected actual]}]
                                       (if (empty? actual)
                                         []
                                         [(format ";; %s%s" text lnum)
                                          (if (seq expected)
                                            (-> failed-result
                                                (update :expected pprint-str)
                                                (update :actual pprint-str)
                                                (e.u.map/map->str [:expected :actual :diffs]))
                                            actual)
                                          ""])))
                             (str/join "\n"))]
                  (e.p.host/append-to-info-buffer host s {:show-temporarily? true})
                  (e.p.host/append-to-info-buffer host summary)))
              (ix/discard))})

(def apply-test-result-to-quickfix
  {:kind e.c.interceptor/test-result
   :enter (-> (fn [{:as ctx :keys [passed failed]}]
                (let [tested-texts (->> (concat passed failed)
                                        (map generate-quickfix-text)
                                        (set))
                      current-list (->> (e.f.quickfix/get-quickfix-list ctx)
                                        (remove #(contains? tested-texts (:text %))))]
                  (->> failed
                       (map #(hash-map :filename (:filename %)
                                       :lnum (:lnum %)
                                       :text (generate-quickfix-text %)
                                       :type "Error"))
                       (concat current-list)
                       (e.f.quickfix/set-quickfix-list ctx))))
              (ix/discard))})

(def store-last-failed-test-query
  {:kind e.c.interceptor/test-result
   :enter (-> (fn [{:component/keys [nrepl session-storage] :keys [failed]}]
                (some->> failed
                         (get-failed-tests-query nrepl)
                         (e.f.s.test/set-last-failed-tests-query session-storage)))
              (ix/discard))})

(def output-test-result-to-cmdline
  {:kind e.c.interceptor/test-result
   :enter (-> (fn [{:component/keys [host] :keys [succeeded? summary]}]
                (if succeeded?
                  (e.message/info host summary)
                  (e.message/error host summary)))
              (ix/discard))})

(def focus-current-testing
  "Re evaluate the current top list with focusing on the current testing form."
  {:kind e.c.interceptor/test
   :enter (-> (fn [{:as ctx :component/keys [host] :keys [line]}]
                (try
                  (let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
                        {:keys [code]} (e.f.sexpr/get-top-list ctx cur-lnum cur-col)
                        code' (e.u.sexpr/convert-code-to-testing-focused-code code (- cur-lnum line -1) cur-col)]
                    (e.f.evaluate/evaluate-code ctx code' {:line line
                                                           :column (:column ctx)
                                                           :ns (:ns ctx)
                                                           :file (:file ctx)}))
                  (catch Exception ex
                    (timbre/debug "Failed to focus on the current testing form" ex)
                    (e.message/error host (format "Failed to focus on the current testing form: %s" (ex-message ex))))))
              (ix/discard))})
