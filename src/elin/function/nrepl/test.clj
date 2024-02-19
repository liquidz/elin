(ns elin.function.nrepl.test
  (:require
   [clojure.edn :as edn]
   [clojure.test :as t]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.component :as e.s.component]
   [malli.core :as m]))

(defn- test-clj-code
  [{:keys [test-vars current-file base-line]}]
  `(let [ignore-keys []
         summary (atom {:test 0 :pass 0 :fail 0 :error 0 :var 0})
         results (atom {})
         testing-var (atom nil)
         testing-ns (atom nil)
         to-str (fn [x]
                  (if (instance? Throwable x)
                    (str (class x) ": " (.getMessage x) (ex-data x))
                    (pr-str x)))
         report (fn [m]
                  (let [report-type (:type m)]
                    (cond
                      (contains? #{:pass :fail :error} report-type)
                      (let [passed? (= :pass report-type)
                            ns' (some-> @testing-var namespace)
                            var' (some-> @testing-var name)
                            file' (or (when-let [file (:file m)]
                                        (when (not= "<expr>" file)
                                          file))
                                      ~current-file)
                            line' (when-let [line (:line m)]
                                    (+ (dec ~base-line) line))
                            m' (-> m
                                   (assoc :ns ns'
                                          :var var'
                                          :file file'
                                          :line line')
                                   (update :type name)
                                   (update :expected to-str)
                                   (update :actual to-str)
                                   (cond-> passed? (dissoc :expected :actual)))]

                        (swap! summary update report-type inc)
                        (swap! results update-in [ns' var'] conj m'))

                      (= :begin-test-var report-type)
                      (let [var-meta (some-> m :var meta)
                            ns-name' (some-> var-meta :ns ns-name str)
                            var-name' (some-> var-meta :name str)]
                        (swap! summary update :test inc)
                        (swap! summary update :var inc)
                        (when (and ns-name' var-name')
                          (reset! testing-var (symbol ns-name' var-name')))
                        (when ns-name'
                          (reset! testing-ns ns-name')))

                      (= :end-test-var report-type)
                      (reset! testing-var nil)

                      :else
                      nil)))]
     (binding [clojure.test/report report]
       ;; Use `test-vars` instead of `test-var` to support fixtures
       (clojure.test/test-vars [~@test-vars]))
     (cond-> {:summary @summary
              :results @results}
       @testing-ns (assoc :testing-ns @testing-ns))))

(defn- babashka?
  [nrepl]
  (contains? (e.p.nrepl/version nrepl)
             :babashka))

(def ^:private ?TestQuery
  [:map
   [:ns string?]
   [:vars [:sequential string?]]
   [:base-line int?]
   [:current-file string?]])

(m/=> test-var-query!! [:=> [:cat e.s.component/?Nrepl ?TestQuery] map?])
(defn test-var-query!!
  [nrepl {ns-str :ns vars :vars base-line :base-line current-file :current-file}]
  (e/let [vars' (map #(symbol (str "#'" %)) vars)
          code (str (test-clj-code {:test-vars vars'
                                    :current-file current-file
                                    :base-line (if (babashka? nrepl)
                                                 base-line
                                                 0)}))
          resp (e.f.nrepl/eval!! nrepl code {:ns ns-str})]
    (edn/read-string (:value resp))))

(comment
  (defmacro run-tests [vars]
    (test-clj-code {:test-vars vars :current-file "dummy.clj" :base-line 0}))

  (t/deftest hello
    (t/is (= {:a 1} {:a 1})))

  (run-tests [#'hello]))
